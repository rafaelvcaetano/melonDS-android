#include "OboeCallback.h"
#include "types.h"
#include "Platform.h"
#include "MelonLog.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>

using namespace melonDS;

#define INTERNAL_FRAME_RATE 59.8260982880808f

// 定义于 MelonDSAndroidJNI.cpp：模拟/JNI 线程写、音频回调线程读（设计 D5）
extern std::atomic<bool> isFastForwardEnabled;
extern std::atomic<float> fps;
extern std::atomic<float> fastForwardSpeedMultiplier;
extern std::atomic<float> fastForwardAudioMuteThreshold;
// 加速音高保持开关（u-stretch-settings 交付的设置链原子全局）：本单元首次在音频回调消费
extern std::atomic<bool> fastForwardPitchPreserve;
extern std::atomic<bool> fastForwardStretchResetPending;

// 常速基准比率：60fps 目标对 NDS 实际帧率的换算（≈1.0029）
static constexpr double kBaseSkewRatio = 60.0 / INTERNAL_FRAME_RATE;

// SPU 环形缓冲为 8192 立体声帧（≈170ms，子模块 SPU.cpp InitOutput 同步扩容：拉伸路径全档位
// 生效后，加速 N 倍的产出必须装得进「两次回调之间」的写入窗口——8× 时每周期写入约 3840 帧，
// 8192 覆盖到 numFrames≈960 的省电模式设备），此处为其半容量。TrimOutput 的「半满」由核心
// 按物理容量算（4096），本常量必须与它保持一致
static constexpr int kAudioOutputHalfCapacity = 4096;

// 全容量 = 2 × 半容量（安全阀阈值与低水位观察线的共同推导基准）
static constexpr int kAudioOutputFullCapacity = 2 * kAudioOutputHalfCapacity;

// 高水位安全阀阈值（设计 D4）：85% 容量。触发后 Trim 把水位放回半满自然退出，
// 无冷却期、无次数上限；正常常速（速率匹配、水位半满以下常态）永不触及
static constexpr int kAudioOutputHighWaterThreshold = (int) (kAudioOutputFullCapacity * 0.85);

// 待首测的回调数兜底上限：测量窗口可能在转换被回调观察到之前已走完（调度延迟），
// 此时 fps 值不再变化、快照比较永不命中，靠超时直接快照当前值（设计 D2 规则 3）
static constexpr int kMaxCyclesAwaitingFirstMeasurement = 8;

// FF-on/档位转换的 skew 种子（设计 D2 规则 4/5）：固定档 = 倍速 × 基准（设备撑得住目标帧率时即
// 精确值）；不限速档保守取 1.3 × 基准——种子必须低于真实倍速（设置项最小定倍速 1.5），让缓冲
// 从空回填而非超产顶样
static double seedSkewForMultiplier(float multiplier)
{
    double speedFactor = multiplier > 0 ? (double) multiplier : 1.3;
    return speedFactor * kBaseSkewRatio;
}

// 实测比率 = 实测帧率 / NDS 实际帧率；fps 尚无测量（0）时按常速基准处理（设计 §7.4）
static double measuredRatioFromFps(float measuredFps)
{
    return (measuredFps > 0.0f ? (double) measuredFps : 60.0) / INTERNAL_FRAME_RATE;
}

// 水位反馈修正因子（设计 D2 规则 6）：fillErr = (填充水位 − 半容量)/半容量 ∈ [−1, 1]，
// 连续增益 K(e) = 0.05 + 0.25|e|——分段增益在区间边界跳变会产生抖振（设计 D2 不采用 (vi)），
// 乘积钳 ±2%：反馈直接调制产样密度，而产样密度就是音调（音调 = 真实倍速 ÷ skew），
// 修正幅度必须压在听觉不可辨的范围内（真机实测 ±10% 会形成可听的音调锯齿）
static double fillFeedbackFactor(int fillLevel)
{
    double fillErr = (fillLevel - kAudioOutputHalfCapacity) / (double) kAudioOutputHalfCapacity;
    return std::clamp(1.0 + (0.05 + 0.25 * std::fabs(fillErr)) * fillErr, 0.98, 1.02);
}

// skew 变化率限制（每回调 ±1%）：skew 经 blip 换算直接决定波形密度，动态跳变既产生
// 帧内时序断裂（blip_set_rates 与模拟线程帧内 blip_add_delta 竞争），也表现为音调阶跃；
// 平滑到目标而非一步跳达。回调周期 4-10ms 下每秒可移动 2.7 倍范围，远快于测量收敛
static double slewLimitSkew(double target, double previous)
{
    return std::clamp(target, previous * 0.99, previous * 1.01);
}

// —— stretch 路径（设计 D4：tempo 控制器 = skew 控制器骨架的执行器替换）——

// tempo 总钳（快照/slew 之后的总算子）：加速场景 tempo ≥ 1（tempo < 1 = 拉伸放慢，语义外）；
// 上界给高倍速留余量：全档位生效后 8× 的 tempo ≈ 8 贴上界，实测速度短暂超档时靠它兜住
//（tempo 超界 = SoundTouch 无法消化产样、积压上涨由安全阀清理）
static constexpr double kStretchTempoMin = 1.0;
static constexpr double kStretchTempoMax = 8.0;

// 成品队列安全阀阈值：150ms 输出时长当量。被控量 = SoundTouch 成品队列（输出 FIFO），
// 反馈基准动态跟随回调帧数（见 onAudioReady）；安全阀只覆盖反馈失效的极端（<0.1%）
static constexpr double kStretchOutValveSec = 0.150;

// tempo 种子 = max(档位倍速, 1.5)（不限速档 multiplier = 0 不能直取，tempo 非法；取设置项
// 最低档 1.5 保守起步，首个测量快照到达后即用真值）——无 kBaseSkewRatio 基准因子是设计 D4
// 的语义决策：skew 种子是产样密度需基准校正，tempo 种子是内容压缩比，两语义不同源；
// 实际倍速与档位值的偏差由「待首测 + 首测快照直接生效」覆盖（首个测量窗口到来前
// SoundTouch 尚在窗口填充期）。SPU 产样速率随 SPU 环形扩容（8192）后恒在搬运窗口内，
// 无需两级速率分配（该形态曾被尝试，因 blip 重采样语义为变调而非音高保持而废止）
static constexpr double kStretchSeedSpeedFloor = 1.5;

// 输入积压时长当量（仅探针观测，2026-10-08 反馈改版后被控量已换成品队列）：SoundTouch
// 喂入未消费样本数折算到输出域时长——M 个输入样本以 tempo 压缩后产出 M/tempo 个输出样本
// @48000Hz。48000 = 本项目固定输出采样率（MelonDSAudio.cpp 流构建同款硬编码）
static double backlogSeconds(uint backlogSamples, double tempo)
{
    return backlogSamples / (48000.0 * tempo);
}

// 水位反馈修正因子（2026-10-08 真机改版：被控量 = SoundTouch 成品队列，非输入积压——
// SoundTouch 来料即加工，输入积压不受 tempo 控制、只能观测到加工窗口零头，旧反馈因此
// 永久饱和在 -2% 档，产出持续高于消费 ~2%，差值全部积分进无人观测的成品队列——真机
// 实测 70s 涨到 1.4s 延迟即此）：成品队列 > 基准 = 产速偏高 → tempo 上调压产；
// 分段增益与 ±2% 乘积钳保留——tempo 就是语速，修正幅度压在听觉不可辨范围内
static double outFifoFeedbackFactor(double outFifoSec, double baselineSec)
{
    double err = (outFifoSec - baselineSec) / baselineSec;
    return std::clamp(1.0 + (0.05 + 0.25 * std::fabs(err)) * err, 0.98, 1.02);
}

// tempo 变化率限制（每回调 ±2%，设计 D4）：stretch 侧比 skew 侧 ±1% 放宽——tempo 波动的
// 听感代价（语速微变）低于音调波动
static double slewLimitTempo(double target, double previous)
{
    return std::clamp(target, previous * 0.98, previous * 1.02);
}

// SoundTouch 构造失败回退（设计 §7.5 D 类）：构造发生在流设置线程，异常逃逸 = 音频断流。
// 就地捕获 → 运行期强制置关（UI 设置值不动，下次流重建静默重试）+ LOG_ERROR 含恢复动作，
// 后续初始化全部按现状路径形态落（stretchActive 判定自然为假）
static soundtouch::SoundTouch initStretcher()
{
    try {
        // C++17 纯右值直造（guaranteed copy elision），无拷贝/移动介入
        return soundtouch::SoundTouch();
    }
    catch (...)
    {
        fastForwardPitchPreserve.store(false, std::memory_order_relaxed);
        LOG_ERROR("melonDS", "SoundTouch stretcher init failed, pitch preservation disabled; re-enable the option or restart the game to retry");
        // catch 内二次构造若再抛即逃逸——首次失败后数微秒内连续分配耗尽的复合极端，
        // 该环境下现状路径的其余分配同样无法完成，无可恢复性可言（<0.1% 的 <0.1%）
        return soundtouch::SoundTouch();
    }
}

#ifndef NDEBUG
// 重填期打点时间戳（steady clock 毫秒，仅 debug 探针用）
static double steadyMs()
{
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
#endif

double OboeCallback::reinitSkewController(float multiplier)
{
    // FF-on 转换初始化（设计 D2 规则 4/7）：种子 + 待首测 + 缓冲余量重建。EMA 同置种子并等
    // 首个新测量直接快照——从保守种子缓慢爬向真值期间持续超产，高倍速下爬升期溢出量会击穿
    // 缓冲（D2 规则 3）。种子是精心选择的初值，直接应用（不经变化率限制）；后续测量微调才受
    // slew 约束。FF-on/热更新重建缓冲余量，冻结态随之作废（常速期安全阀事件的残留冻结不应
    // 泄漏进补水窗口——该期反馈方向恰为超产补水，冻结帮倒忙）。
    // 返回种子值供调用方赋给本回调的 skew 局部变量
    double seed = seedSkewForMultiplier(multiplier);
    emaMeasuredRatio = seed;
    awaitingFirstMeasurement = true;
    fpsAtTransition = fps.load(std::memory_order_acquire);
    awaitingFirstMeasurementCycles = 0;
    pendingBufferRebuild = true;
    feedbackFreezeCycles = 0;
    appliedSkew = seed;
    return seed;
}

OboeCallback::OboeCallback(int volume, void (*onErrorCallback)(void), std::ostream* recordingStream) : _volume(volume), onErrorCallback(onErrorCallback), _recordingStream(recordingStream), _stretch(initStretcher()) {
    // 控制器状态按出生态初始化（设计 D2 规则 8）：音频流重建会新造本对象，此时加速可能早已开启、
    // 标志翻转永不发生——构造时已处于加速态 = 按一次 FF-on 转换初始化
    bool fastForward = isFastForwardEnabled.load(std::memory_order_acquire);
    float multiplier = fastForwardSpeedMultiplier.load(std::memory_order_relaxed);

    lastFastForwardEnabled = fastForward;
    lastMultiplier = multiplier;
    fpsAtTransition = fps.load(std::memory_order_acquire);
    awaitingFirstMeasurementCycles = 0;
    pendingBufferRebuild = false;
    // 迟滞静音态按出生态复位（设计 D2 规则 8 生命周期表）；EMA 初值已含 fps 快照成分，
    // 首个回调的出生态首判定即能正确重进静音/发声，无「种子先有声、首测再静音」的二次阶跃
    audioMuted = false;

    if (fastForward)
    {
        // EMA 初始值 = max(种子, 当前 fps 快照/基准)（设计 D2 规则 8）：fps 快照是最近一个
        // 测量窗口的实测，流重建前已实测超线时出生首判定即有据可依；快照尚无测量（0）时
        // measuredRatioFromFps 按常速基准处理，max 退化为种子本身
        emaMeasuredRatio = std::max(seedSkewForMultiplier(multiplier),
                                    measuredRatioFromFps(fps.load(std::memory_order_acquire)));
        awaitingFirstMeasurement = true;
        appliedSkew = emaMeasuredRatio;
        // 缓冲余量重建推迟到首个回调、在读取输出之后执行（动作次序见 onAudioReady）——
        // 构造时实例可能尚未绑定，也没有本回调的读取需要保护
        pendingBufferRebuild = true;
    }
    else
    {
        emaMeasuredRatio = kBaseSkewRatio;
        awaitingFirstMeasurement = false;
        appliedSkew = kBaseSkewRatio;
    }
    feedbackFreezeCycles = 0;
    valveLogCooldown = 0;
    stretchErrorLogCooldown = 0;

    // stretch 侧出生态初始化（设计 D4 出生态规则）：构造时已处 stretch 态 = 按一次 stretch
    // 转换初始化（音频流重建即对象重建，「进入 stretch」转换在出生时永不发生）。
    // initStretcher 失败回退已把开关置关，此处读到的必为 false → bornStretch 为假 →
    // tempo 侧按惰性初值落，控制器全部按现状路径初始化（§7.5 D 类回退语义）。
    // 全档位生效（2026-10-07 用户裁决）：stretchActive 不再含档位条件
    bool pitchPreserve = fastForwardPitchPreserve.load(std::memory_order_relaxed);
    bool bornStretch = fastForward && pitchPreserve;
    lastStretchActive = bornStretch;
    tempoAwaitingFirstMeasurementCycles = 0;
    backlogFreezeCycles = 0;
    stretchValveLogCooldown = 0;
#ifndef NDEBUG
    // 探针状态随流重建（新对象）归零（OboeCallback.h 成员声明处的残留形态说明）
    stretchSpuPeakFill = 0;
    stretchRefillActive = false;
#endif
    if (bornStretch)
    {
        // 与回调内 stretch 转换初始化同构；EMA 初值含 fps 快照成分（同构 skew 侧出生态，
        // D2 规则 8 迁移）。无 clear 必要——实例全新无积压
        double seed = std::max((double) multiplier, kStretchSeedSpeedFloor);
        float bornFps = fps.load(std::memory_order_acquire);
        tempoEmaMeasuredRatio = std::max(seed, measuredRatioFromFps(bornFps));
        tempoAwaitingFirstMeasurement = true;
        tempoFpsAtTransition = bornFps;
        appliedTempo = seed;
    }
    else
    {
        tempoEmaMeasuredRatio = 1.0;
        tempoAwaitingFirstMeasurement = false;
        appliedTempo = 1.0;
    }

    // 拉伸器固定配置：输出采样率/通道数 = 本项目音频流固定形态（48000/立体声，
    // MelonDSAudio.cpp 流构建同款硬编码）；quickseek 初值 0 关闭（⏳ 真机定档后改常量
    // 重编译，不新增配置链字段，设计 §5.3）。setTempo = 出生态种子（bornStretch 时即原
    // bornStretch 分支内的 setTempo(seed)，非 stretch 出生 1.0 同样无副作用）。
    // 配置链路含堆分配（setSampleRate → TDStretch 缓冲 new，三 skill 裁决 20261007 固 2），
    // 与 initStretcher 同款 D 类防护：失败置关 + LOG_ERROR；此刻 stretch 侧出生态成员已按
    // bornStretch 落，置关后首回调 stretchActive 恒假并经退出分支重初始化 skew 控制器，
    // tempo 侧成员闲置不被消费，无脏状态
    try {
        _stretch.setTempo(appliedTempo);
        _stretch.setSampleRate(48000);
        _stretch.setChannels(2);
        _stretch.setSetting(SETTING_USE_QUICKSEEK, 0);
        // SEQUENCE_MS 显式 40：默认 auto（@48kHz 约 80ms）使加工窗口沉淀约 56ms 内容的输入
        // 水位，是拉伸路径音画延迟的最大可动项——真机探针实测（2026-10-08）积压仅 16-40ms
        // 且调低积压基准压不动它（水位由本参数决定）；40ms 窗口换更低延迟，代价为拉伸颗粒
        // 变粗（seekwindow/overlap 保持 auto，随 sequence 等比缩小）
        _stretch.setSetting(SETTING_SEQUENCE_MS, 40);
    }
    catch (...)
    {
        fastForwardPitchPreserve.store(false, std::memory_order_relaxed);
        LOG_ERROR("melonDS", "SoundTouch stretcher setup failed, pitch preservation disabled; re-enable the option or restart the game to retry");
    }
}

oboe::DataCallbackResult
OboeCallback::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    auto currentInstance = activeInstance.lock();

    if (!currentInstance)
    {
        memset(audioData, 0, numFrames * sizeof(u16) * 2);
        return oboe::DataCallbackResult::Continue;
    }

    bool fastForward = isFastForwardEnabled.load(std::memory_order_acquire);
    float multiplier = fastForwardSpeedMultiplier.load(std::memory_order_relaxed);

    // 两路径分流（2026-10-07 用户裁决：音高保持全档位生效，原「固定档 ≤4×」档位条件废止；
    // .tmp/dev-flow/two-level-rate-design.md）：高倍速下 SPU 产样速率超出搬运窗口的容量账由
    // SPU 环形扩容（2048→8192，子模块 SPU.cpp InitOutput）解决，不在路径判定上分流。
    // 开关 relaxed 读、门控于 acquire 读的加速标志（与阈值静音同款内存序论证）
    bool stretchActive = fastForward && fastForwardPitchPreserve.load(std::memory_order_relaxed);

    // 读档/rewind 内容不连续（设计 D3 第五类）：置位即消费清零（非 stretch 路径下也清，
    // 防标志跨路径残留）；stretch 路径下触发 SoundTouch.clear + tempo 转换初始化。
    // 常态（标志恒假）只有一次 relaxed 原子读——exchange 是写操作，先 load 判真再清，
    // 现状路径的新增开销收敛到设计 §7.2 口径（每回调两次 relaxed 读：stretch 开关 + 本标志）
    bool stretchResetTriggered = false;
    if (fastForwardStretchResetPending.load(std::memory_order_relaxed))
        stretchResetTriggered = fastForwardStretchResetPending.exchange(false, std::memory_order_relaxed);

    // 退出 stretch 而加速保持开（关开关/档位切到 8×/不限速）时，现状控制器看不到任何标志
    // 变化——显式调用转换初始化重置 skew 控制器（设计 D3 退出规则；三 skill 裁决 20261007
    // 审 2 改显式调用，原「强置 lastFastForwardEnabled 触发既有转换分支」的信号伪造形态废弃）。
    // SoundTouch 积压就地废弃（D3 口径：接受 ≤积压当量的内容跳变），下次进入 stretch 时
    // 转换初始化统一 clear。加速已关时无需处理（常速分支不依赖控制器状态）
    if (!stretchActive && lastStretchActive)
        reinitSkewController(multiplier);

    // 填充水位：安全阀与水位反馈共用这一次读取（设计 §7.5）；下方缓冲重建路径另有一次独立
    // 读取，取的是本回调消费后的新鲜水位，两处目的不同不共用
    int fillLevel = currentInstance->getAudioOutputSize();

    // 高水位安全阀（设计 D4，音频活跃期间不限加速状态）：贴满态一次性 Trim 回半满，
    // 覆盖常速贴满（如短于一个回调周期的加速脉冲遗留）与流重建积压。无需再比半容量：
    // 85% 触发阈值天然高于半容量——水位 ≤ 半容量时该条件永不成立，Trim 反而会把
    // 读指针向回拨、重播已消费样本。
    // 三项修复（真机实测缺陷）：①日志冷却——实时线程高频日志是纯噪声；②Trim 后冻结反馈——
    // Trim 把水位阶跃回半满，立即反馈会读到假低水位而撤销修正，随超产回贴顶形成音调锯齿；
    // ③EMA 上探——贴顶 = 产样相对消费不足（skew 偏小，前馈偏低），每次触发按 2% 步进校正，
    // 让前馈在低性能设备的大幅速度波动下仍能跟上真实速度。
    // stretch 路径下挂起（设计 D4/§7.4）：观测对象已换成 SoundTouch 积压，Trim 重置 SPU
    // 水位对积压观测无意义；其 EMA 上探产物在「SPU 高水位 + stretch」共现形态下逐回调抬
    // tempo 2% 属失控形态。SPU 溢出保护链见 §7.4（档位分流 + NDEBUG 水位探针兜底观测）
    if (!stretchActive && fillLevel > kAudioOutputHighWaterThreshold)
    {
        currentInstance->trimAudioOutput();
        feedbackFreezeCycles = 10;
        if (!awaitingFirstMeasurement)
            emaMeasuredRatio *= 1.02;

        if (valveLogCooldown > 0)
            valveLogCooldown--;
        else
        {
            LOG_WARN("melonDS", "audio output high-water safety valve: fill=%d > %d, trimming to half capacity", fillLevel, kAudioOutputHighWaterThreshold);
            valveLogCooldown = 100; // ≈0.4-1 秒一条，触发段只留频率证据不留洪泛
        }
        // 水位置半满放在日志之后：日志要打的是触发时水位；本回调后续的水位反馈按半满计算
        // （TrimOutput 语义：水位一次性重置为半满，设计 §4.2）
        fillLevel = kAudioOutputHalfCapacity;
    }

    int num_in;
    if (stretchActive)
    {
        // ===== stretch 路径（设计 §7.2 读路径 + D4 tempo 控制器；2026-10-08 反馈改版）=====
        // 被控量 = SoundTouch 成品队列（输出 FIFO，numSamples，输出帧直读时长）：产/消平衡
        // 的唯一积分器——tempo 偏低则成品持续堆积、偏高则持续欠产断流，反馈在此闭环。
        // 输入积压（numUnprocessedSamples）不受 tempo 控制（来料即加工），仅作探针观测。
        // 基准动态跟随回调帧数：numFrames（本回调取用量）+ 1536 帧（32ms，覆盖最小 tempo
        // 1.5 下一窗成品的锯齿幅度 1920/1.5≈1280 帧不击穿），转换/安全阀 clear 后归零
        double outFifoSec = _stretch.numSamples() / 48000.0;
        double outBaselineSec = ((double) numFrames + 1536.0) / 48000.0;
        double inBacklogSec = backlogSeconds(_stretch.numUnprocessedSamples(), appliedTempo);
        double tempo;
        if (!lastStretchActive || multiplier != lastMultiplier || stretchResetTriggered)
        {
            // stretch 转换（设计 D3 进入规则）：进入 stretch（路径切换/流重建出生后首回调）、
            // 加速中倍速热更新（含跨全档位边界 2×↔8× 往返）、读档/rewind 内容不连续——
            // SoundTouch.clear() 全清（输入输出双清，积压废弃，重填期口径 §5.2）+ tempo
            // 转换初始化：种子 = max(档位倍速, 1.5)（不限速档 multiplier=0 兜底）+ 待首测 +
            // 首测快照直接生效（不经 slew）。种子是精心选择的初值，直接应用
            _stretch.clear();
            double seed = std::max((double) multiplier, kStretchSeedSpeedFloor);
            tempoEmaMeasuredRatio = seed;
            tempoAwaitingFirstMeasurement = true;
            tempoFpsAtTransition = fps.load(std::memory_order_acquire);
            tempoAwaitingFirstMeasurementCycles = 0;
            backlogFreezeCycles = 0;
            outFifoSec = 0.0;
            tempo = seed;
        }
        else if (outFifoSec > kStretchOutValveSec)
        {
            // 成品队列安全阀（设计 D4 极端兜底，<0.1%）：反馈失效导致成品堆积失控时
            // clear() 全清重填（SoundTouch 无逐样本丢弃 API，全清是既定落地形态）。
            // 重初始化 = 转换规则 + EMA 上探——堆积 = 产速偏高即前馈偏高，上探把 tempo
            // 顶过真值加速排空；反馈冻结 10 回调（照搬 Trim 后冻结形态，防读到假低水位）
            _stretch.clear();
            double seed = std::max((double) multiplier, kStretchSeedSpeedFloor);
            tempoEmaMeasuredRatio = seed;
            tempoEmaMeasuredRatio *= 1.02;
            tempoAwaitingFirstMeasurement = true;
            tempoFpsAtTransition = fps.load(std::memory_order_acquire);
            tempoAwaitingFirstMeasurementCycles = 0;
            backlogFreezeCycles = 10;
            if (stretchValveLogCooldown > 0)
                stretchValveLogCooldown--;
            else
            {
                LOG_WARN("melonDS", "stretch outfifo safety valve: %.1fms > %dms, clearing stretcher",
                         outFifoSec * 1000.0, (int) (kStretchOutValveSec * 1000.0));
                stretchValveLogCooldown = 100; // ≈0.4-1 秒一条，触发段只留频率证据不留洪泛
            }
            outFifoSec = 0.0;
            tempo = tempoEmaMeasuredRatio;
        }
        else
        {
            // 触发条件与 skew 侧转换规则族同构（D4）：待首测（fps 值变化 = 新测量到达，
            // 回调数上限兜底「测量已发生但值碰撞」）→ 快照直接生效；否则 EMA 平滑 +
            // 成品队列反馈 + slew
            bool snapshotApplied = false;
            float measuredFps = fps.load(std::memory_order_acquire);
            if (tempoAwaitingFirstMeasurement)
            {
                if (measuredFps != tempoFpsAtTransition || ++tempoAwaitingFirstMeasurementCycles >= kMaxCyclesAwaitingFirstMeasurement)
                {
                    tempoEmaMeasuredRatio = measuredRatioFromFps(measuredFps);
                    tempoAwaitingFirstMeasurement = false;
                    snapshotApplied = true;
                }
            }
            else
            {
                tempoEmaMeasuredRatio = 0.5 * measuredRatioFromFps(measuredFps) + 0.5 * tempoEmaMeasuredRatio;
            }

            if (snapshotApplied)
            {
                // 首测快照直接生效（不经反馈、不经 slew）——快照是测量窗口的真值，同构 skew 侧
                tempo = std::clamp(tempoEmaMeasuredRatio, kStretchTempoMin, kStretchTempoMax);
            }
            else
            {
                // EMA 前馈目标 × 成品队列反馈修正，再总钳（反馈作用在前馈目标之后、总钳之前）。
                // 反馈冻结期 factor 取 1——clear 造成的假空队列不参与修正，等真实水位重新形成
                double target = tempoEmaMeasuredRatio;
                if (backlogFreezeCycles > 0)
                    backlogFreezeCycles--;
                else
                    target *= outFifoFeedbackFactor(outFifoSec, outBaselineSec);

                tempo = std::clamp(slewLimitTempo(target, appliedTempo), kStretchTempoMin, kStretchTempoMax);
            }
        }

        appliedTempo = tempo;
        _stretch.setTempo(tempo);
        // SPU 满速产样（设计 §4.1）：skew 固定 1.0，速率匹配全交 SoundTouch（tempo = 实测
        // 速度，变速不变调）。全档位生效的前提是 SPU 环形扩容（8192）：加速 N 倍的产样速率
        // （N×48000）必须装得进两次回调之间的写入窗口——见 kAudioOutputHalfCapacity 注释。
        // 原恒 1.0 在 2048 容量下正是 8× 超窗丢样的根源，也是两级速率分配被尝试又废止的原因
        //（blip 重采样语义为变调，skew < 1 会压低音调）
        currentInstance->setAudioOutputSkew(1.0);
        // SPU 侧缓冲余量重建的语义在 stretch 下由 SoundTouch.clear 承担（§7.2）；挂起的
        // skew 控制器残留的待重建标记（含出生态置位——出生即 stretch 时首回调不走转换分支）
        // 在此无条件消费掉，防脏状态跨路径滞留
        pendingBufferRebuild = false;
        // 转换检测的比较基准在 stretch 下也必须推进，否则倍速热更新后每回调重复转换
        lastMultiplier = multiplier;
        lastStretchActive = true;

#ifndef NDEBUG
        // stretch 路径替代观测（设计 §7.2，读取前执行的位置语义保留）：积压时长当量
        // 服务积压定档与安全阀观测（每回调打点，验收 grep 采样）；SPU 水位峰值/贴满事件
        // 服务 §7.4 回调周期前提的观测。低水位观察线在 stretch 下是常态形态（每回调读空），
        // 诊断语义失效不再触发（stretch 分支内不挂原探针）。
        // TODO(simplify): 本块与 refill 段共 6 处 LOG_DEBUG 共用 "audio-probe " 前缀，探针
        // 数量增长后可收为变参宏（code-simplify 20261007 TODO-1，当前净收益≈0 不立改）
        LOG_DEBUG("melonDS", "audio-probe stretch backlog: inbkMs=%.1f outMs=%.1f ff=%d fps=%.1f tempo=%.3f spu=%d",
                  inBacklogSec * 1000.0, outFifoSec * 1000.0, fastForward,
                  fps.load(std::memory_order_relaxed), appliedTempo, fillLevel);
        // 探针状态为成员（OboeCallback.h）：随流重建（对象重建）重置，防跨流残留
        if (fillLevel > stretchSpuPeakFill)
        {
            stretchSpuPeakFill = fillLevel;
            LOG_DEBUG("melonDS", "audio-probe spu peak: fill=%d new max", fillLevel);
        }
        if (fillLevel >= kAudioOutputFullCapacity)
            LOG_DEBUG("melonDS", "audio-probe spu full: fill=%d, SPU ring buffer full, oldest samples dropped", fillLevel);
#endif

        // 每回调把 SPU 缓冲读空搬运（设计 §7.2；读空后水位归零是 stretch 常态，§7.4 容量账前提）。
        // SoundTouch 输入队列增长含堆分配抛点（FIFOSampleBuffer 扩容 new + ST_THROW_RT_ERROR），
        // 回调线程异常逃逸 = std::terminate——就地捕获降级本回调硬零 + 限频 LOG_ERROR
        // （三 skill 裁决 20261007 固 1；与 initStretcher 同款 D 类形态）
        try {
            int available = currentInstance->getAudioOutputSize();
            if (available > 0)
            {
                if (available > kAudioOutputFullCapacity)
                    available = kAudioOutputFullCapacity; // GetOutputSize 环形语义下占用恒 ≤ 容量（写满时
                                                           // 读指针前移、占用 = 容量 2048），此 clamp 不触发；
                                                           // 显式边界 = 搬运缓冲容量契约，防容量漂移时越界写
                currentInstance->readAudioOutput(stretchTransferBuffer, available);
                _stretch.putSamples(stretchTransferBuffer, (uint) available);
            }
            num_in = (int) _stretch.receiveSamples((s16*) audioData, (uint) numFrames);
        }
        catch (...)
        {
            if (stretchErrorLogCooldown > 0)
                stretchErrorLogCooldown--;
            else
            {
                LOG_ERROR("melonDS", "stretcher processing failed, outputting silence; re-enable the option or restart the game to retry");
                stretchErrorLogCooldown = 600; // ≈2.4-6 秒一条，极端故障段只留频率证据不留洪泛
            }
            memset(audioData, 0, numFrames * sizeof(s16) * 2);
            return oboe::DataCallbackResult::Continue;
        }
        int got = num_in;

#ifndef NDEBUG
        // 重填期打点（设计 §7.2）：got=0 段起止时间戳与恢复点，服务 S5/S6 重填期
        // ≤200ms 口径的机器计时（logcat 量时长，不依赖听感判定）。
        // 探针状态为成员（OboeCallback.h）：随流重建重置，防旧流 start 拼进新流计时
        if (got < 1)
        {
            if (!stretchRefillActive)
            {
                stretchRefillActive = true;
                LOG_DEBUG("melonDS", "audio-probe refill start: t=%.1fms outMs=%.1f", steadyMs(), outFifoSec * 1000.0);
            }
        }
        else if (stretchRefillActive)
        {
            stretchRefillActive = false;
            LOG_DEBUG("melonDS", "audio-probe refill end: t=%.1fms got=%d outMs=%.1f (output resumed)", steadyMs(), got, outFifoSec * 1000.0);
        }
#endif
    }
    else
    {
        // ===== 现状路径（常速旁路 / 现状控制器 / stretch 开 + 8× 或不限速档）=====
        // 以下逻辑相对本设计交付基线（rebase 前第一代落地 commit，见 .tmp/dev-flow 工作流档案）
        // 行为逐位保留（回归红线），仅两处结构改动：整体缩进一级 + FF-on 转换初始化抽为
        // reinitSkewController（行为等价重构，三 skill 裁决 20261007 审 2）
        double skew;
        if (fastForward)
        {
            if (!lastFastForwardEnabled || multiplier != lastMultiplier)
            {
                // FF-on 转换或加速中倍速热更新（设计 D2 规则 4/7）：种子 + 待首测 + 缓冲余量
                // 重建（reinitSkewController，与退出 stretch 的重初始化共用；三 skill 裁决
                // 20261007 审 2）。从保守种子缓慢爬向真值期间持续超产，高倍速下爬升期溢出量
                // 会击穿缓冲（D2 规则 3）。种子是精心选择的初值，直接应用（不经变化率限制）；
                // 后续测量微调才受 slew 约束
                skew = reinitSkewController(multiplier);
            }
            else
            {
                bool snapshotApplied = false;
                float measuredFps = fps.load(std::memory_order_acquire);
                if (awaitingFirstMeasurement)
                {
                    // fps 值变化即视为新测量到达；回调数上限兜底「测量已发生但值碰撞」的情形
                    if (measuredFps != fpsAtTransition || ++awaitingFirstMeasurementCycles >= kMaxCyclesAwaitingFirstMeasurement)
                    {
                        emaMeasuredRatio = measuredRatioFromFps(measuredFps);
                        awaitingFirstMeasurement = false;
                        snapshotApplied = true;
                    }
                }
                else
                {
                    // EMA 平滑测量窗口噪声；系数 0.5/回调（真机实测 0.25 在低性能设备的
                    // 大幅速度波动下跟踪误差过大，是前馈偏低与安全阀频触的主因之一）
                    emaMeasuredRatio = 0.5 * measuredRatioFromFps(measuredFps) + 0.5 * emaMeasuredRatio;
                }

                if (snapshotApplied)
                {
                    // 首测快照与种子同款直接生效（不经反馈、不经 slew）：快照是测量窗口的真值，
                    // 若被 ±1%/回调平滑，从种子滑向真值期间持续欠产/超产——低性能形态（真实
                    // 比率≈1.0、种子 1.3）下会拖出数百毫秒的缓冲空窗硬静音；D2 规则 5 的首窗
                    // 溢出余量推导同样以「快照即生效」为前提
                    skew = std::clamp(emaMeasuredRatio, 1.0, 64.0);
                }
                else
                {
                    // EMA 前馈目标 × 水位反馈修正，再总钳（设计 D2 规则 2/6：反馈作用在前馈目标之后、
                    // 总钳之前）。反馈冻结期（安全阀 Trim 后）factor 取 1——Trim 造成的假低水位
                    // 不参与修正，等真实水位重新形成
                    double target = emaMeasuredRatio;
                    if (feedbackFreezeCycles > 0)
                        feedbackFreezeCycles--;
                    else
                        target *= fillFeedbackFactor(fillLevel);

                    // 变化率限制：目标值平滑到达，消除音调阶跃与 blip 帧内密度断裂（常速分支、
                    // 转换分支与首测快照不受此约束——前两者设计锁定直接生效，快照是测量真值）
                    skew = std::clamp(slewLimitSkew(target, appliedSkew), 1.0, 64.0);
                }
            }
        }
        else
        {
            // 常速分支与改动前逐位一致（回归隔离，设计 §7.4）；加速→关的转换不特殊处理：
            // skew 立即回常速值、缓冲不动（D3——残留加速期内容以密度连续方式滑回正常音调）
            skew = std::clamp(60.0 / INTERNAL_FRAME_RATE, 0.995, 1.005);
        }

        lastFastForwardEnabled = fastForward;
        lastMultiplier = multiplier;
        appliedSkew = skew;

        currentInstance->setAudioOutputSkew(skew);

#ifndef NDEBUG
        // 运行时探针（设计 §7.3 P-fill 的落码）：debug 构建记录低水位事件（观察线 = 常速单帧
        // 写入量 ≈ 800 帧的 40%——绝对口径，不随环形容量常量缩放：容量扩容后比例口径会大于
        // 常速稳态存量、导致日志恒触发），供真机标定与 underrun 诊断。放在读取输出之前——
        // 缓冲读空（fill=0，最需诊断的形态）会在下方走硬零提前返回，探针必须先于它执行。
        // 高水位越界由安全阀 WARN 记录
        if (fillLevel < 307)
            LOG_DEBUG("melonDS", "audio-probe low fill: fill=%d ff=%d fps=%.1f ema=%.3f skew=%.3f",
                      fillLevel, fastForward, fps.load(std::memory_order_relaxed), emaMeasuredRatio, skew);
#endif

        num_in = currentInstance->readAudioOutput((s16*) audioData, numFrames);

        // 缓冲余量重建（设计 D2 规则 4）：动作次序固定为先读取后重建——先清后读会让本回调整段输出硬零。
        // 固定档仅在水位高于半容量时 Trim：TrimOutput 无条件把水位重置为半满，水位 ≤ 半容量时余量已足够，
        // 执行反而会把读指针向回拨、重播已消费样本；不限速档全清，为首个 3 帧测量窗口留出溢出余量
        if (pendingBufferRebuild)
        {
            if (multiplier > 0)
            {
                if (currentInstance->getAudioOutputSize() > kAudioOutputHalfCapacity)
                    currentInstance->trimAudioOutput();
            }
            else
                currentInstance->drainAudioOutput();

            pendingBufferRebuild = false;
        }

        lastStretchActive = false;
#ifndef NDEBUG
        // 退出 stretch 时关闭重填段打点状态：stretch 中 got=0 段中途退出（松加速/关选项/切档）
        // 后，遗留 active 会使下次进入 stretch 的首个 got=0 段漏打 start（起止打点不成对）
        stretchRefillActive = false;
#endif
    }

    if (num_in < 1)
    {
        memset(audioData, 0, numFrames * sizeof(s16) * 2);
        return oboe::DataCallbackResult::Continue;
    }

    if (_volume < 256)
    {
        s16* samples = (s16*) audioData;
        for (int i = 0; i < num_in * 2; i++)
            samples[i] = ((s32) samples[i] * _volume) >> 8;
    }

    if (num_in < numFrames)
    {
        int last = num_in - 1;

        for (int i = num_in; i < numFrames; i++)
            ((u32*)audioData)[i] = ((u32*)audioData)[last];
    }

    // 阈值静音判定（设计 D7/U4）：输出侧过滤器，与速率匹配正交——读取与 skew/tempo 计算
    // 照常执行，静音不停速率匹配。迟滞双线：静音线 = 阈值、恢复线 = 0.8 × 阈值（实测比率
    // 围绕阈值波动时不反复切换 = 无连续咔哒）。判定输入是 EMA 裸值（不含反馈与总钳，那两个
    // 为水位控制服务）。stretch 分支下判定输入切到 tempo 侧活跃 EMA（设计 D4 正交性——
    // skew 侧 EMA 挂起不再更新，用它判定会读到陈旧值）
    float muteThreshold = fastForwardAudioMuteThreshold.load(std::memory_order_relaxed);
    if (!fastForward || muteThreshold <= 0.0f)
    {
        // 非加速期恒不静音（标志翻转为关立即复位，无静音残留）；「从不」档（0，缺省）恒不触发，
        // 回调路径零行为差异（U4-5）
        audioMuted = false;
    }
    else if (stretchActive)
    {
        // stretch 分支：迟滞双线与复位规则同构现状，仅判定输入源不同
        if (audioMuted)
            audioMuted = tempoEmaMeasuredRatio > muteThreshold * 0.8;
        else
            audioMuted = tempoEmaMeasuredRatio > muteThreshold;
    }
    else if (audioMuted)
    {
        audioMuted = emaMeasuredRatio > muteThreshold * 0.8;
    }
    else
    {
        audioMuted = emaMeasuredRatio > muteThreshold;
    }

    // 静音置零与录制分支均非热点，不加优化提示属性——构建链声明 C++17（app/build.gradle.kts
    // cppFlags），unlikely 是 C++20 属性，旧版 clang 会报未知属性新警告
    if (audioMuted)
        memset(audioData, 0, numFrames * sizeof(s16) * 2);

    if (_recordingStream)
        _recordingStream->write((char*) audioData, numFrames * sizeof(s16) * 2);

    return oboe::DataCallbackResult::Continue;
}

void OboeCallback::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result)
{
    if (result == oboe::Result::ErrorDisconnected && onErrorCallback != nullptr) {
        onErrorCallback();
    }
}
