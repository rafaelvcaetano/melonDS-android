#ifndef MELONDS_OBOECALLBACK_H
#define MELONDS_OBOECALLBACK_H

#include <oboe/Oboe.h>
#include <fstream>
#include "MelonInstance.h"
#include "SoundTouch.h"

class OboeCallback : public oboe::AudioStreamCallback {
private:
    int _volume;
    void (*onErrorCallback)(void);
    std::ostream* _recordingStream;

    // 加速期 skew 控制器状态（设计 D2），仅构造函数与音频回调线程访问
    bool lastFastForwardEnabled;        // 上一回调周期的加速标志快照（转换检测）
    float lastMultiplier;               // 上一回调周期的倍速快照（加速中倍速热更新检测）
    double emaMeasuredRatio;            // 实测比率（fps/基准帧率）的 EMA 平滑值——加速期 skew 的前馈主部
                                        // （稳态 skew = 本值 × 水位反馈再总钳，见 onAudioReady；转换回调时等于种子）
    bool awaitingFirstMeasurement;      // FF-on 后等待首个新 fps 测量（到达时直接快照 EMA）
    float fpsAtTransition;              // 置待首测时刻的 fps 快照（值变化 = 新测量到达）
    int awaitingFirstMeasurementCycles; // 待首测已等待的回调数（超时兜底快照）
    bool pendingBufferRebuild;          // 本回调读取输出后需执行的缓冲余量重建（FF-on/档位转换）
    bool audioMuted;                    // D7 迟滞静音态：超过静音线置真、低于恢复线（0.8×阈值）复位；
                                        // 流重建（新构造）与加速标志翻转为关时复位为未静音
    double appliedSkew;                 // 上一回调实际应用的 skew（变化率限制的基准；转换/常速分支直接重设）
    int feedbackFreezeCycles;           // 安全阀 Trim 后的反馈冻结计数——Trim 把水位阶跃回半满，
                                        // 立即反馈会读到假低水位而撤销修正、随超产回贴形成锯齿（真机实测缺陷）
    int valveLogCooldown;               // 安全阀日志冷却计数（实时线程高频日志是纯噪声，触发段降频）
    int stretchErrorLogCooldown;        // stretch 搬运异常的日志冷却计数（FIFOSampleBuffer 扩容抛点
                                        // 的降级路径，三 skill 裁决 20261007 固 1；常态恒不触发）

    // stretch 路径（加速 + 音高保持开，全档位生效——2026-10-07 用户裁决；高倍速搬运带宽由
    // SPU 环形扩容 8192 解决，子模块 SPU.cpp InitOutput 同步）的 tempo 控制器状态（设计 D4），
    // 与上方 skew 控制器状态互斥活跃：stretch 下 skew 侧挂起（skew 恒 1.0，速率匹配全交
    // SoundTouch），路径切换经 reinitSkewController 显式重初始化。仅构造函数与音频回调线程访问
    soundtouch::SoundTouch _stretch;     // 时间拉伸器：构造即创建不懒建（设计 §7.2），生命周期随流重建对齐
    s16 stretchTransferBuffer[8192 * 2]; // SPU→SoundTouch 搬运缓冲（8192 立体声帧 = SPU 环形
                                         // 全容量，子模块 SPU.cpp InitOutput 扩容后同步；回调内零堆分配）
    bool lastStretchActive;             // 上一回调周期的 stretch 激活快照（路径切换检测）
    double tempoEmaMeasuredRatio;       // 实测倍速（fps/基准帧率）的 EMA 平滑值——tempo 前馈主部；
                                        // stretch 期迟滞静音的判定输入（skew 侧 EMA 挂起，设计 D4）
    double appliedTempo;                // 上一回调实际应用的 tempo（slew 基准；转换/快照分支直接重设）
    bool tempoAwaitingFirstMeasurement; // 进入 stretch 后等待首个新 fps 测量（到达时直接快照生效）
    float tempoFpsAtTransition;         // 置待首测时刻的 fps 快照（值变化 = 新测量到达）
    int tempoAwaitingFirstMeasurementCycles; // 待首测已等待的回调数（超时兜底快照）
    int backlogFreezeCycles;            // 积压安全阀 clear 后的反馈冻结计数——clear 把积压清零，
                                        // 立即反馈会读到假低积压而压错方向（照搬 Trim 后冻结形态）
    int stretchValveLogCooldown;        // 积压安全阀日志冷却计数（同 valveLogCooldown 形态）

#ifndef NDEBUG
    // §7.2 替代观测探针状态：随对象生命周期（流重建 = 新对象）自然重置——static 局部会跨流
    // 残留，把旧流重填段的 start 时间戳拼进新流（S6「重建后 ≤200ms 恢复输出」的机器计时起点
    // 应为重建时点）或令再进入 stretch 的首个 got=0 段漏打 start（起止打点不再成对）
    double stretchSpuPeakFill;          // SPU 水位峰值观测（§7.4 回调周期前提的观测服务）
    bool stretchRefillActive;           // got=0 重填段打点状态（在段内标记；退出 stretch 路径时复位）
#endif

public:
    std::weak_ptr<MelonDSAndroid::MelonInstance> activeInstance;

    OboeCallback(int volume, void (*onErrorCallback)(void)) : OboeCallback(volume, onErrorCallback, nullptr) { };
    OboeCallback(int volume, void (*onErrorCallback)(void), std::ostream* recordingStream);
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;

private:
    // FF-on 转换初始化（种子 + 待首测 + 缓冲余量重建，设计 D2 规则 4/7）：转换分支与退出
    // stretch 的重初始化共用（三 skill 裁决 20261007 审 2，替代强置 lastFastForwardEnabled 的
    // 信号伪造形态）；返回种子值供调用方赋给本回调的 skew 局部变量
    double reinitSkewController(float multiplier);
};


#endif //MELONDS_OBOECALLBACK_H
