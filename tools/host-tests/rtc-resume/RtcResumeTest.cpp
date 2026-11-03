#include "Boundary.h"
#include <Args.h>
#include <cstdio>
#include <cstdlib>
#include <memory>

static int failures;
static void expect(bool ok, const char* what)
{
    std::printf("%s: %s\n", ok ? "PASS" : "FAIL", what);
    if (!ok) ++failures;
}
static void set(melonDS::RTC& rtc, std::time_t epoch)
{
    const auto tm = *std::localtime(&epoch);
    rtc.SetDateTime(tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec);
}
static std::time_t read(melonDS::RTC& rtc)
{
    int y, m, d, h, i, s;
    rtc.GetDateTime(y, m, d, h, i, s);
    std::tm tm{};
    tm.tm_year = y - 1900; tm.tm_mon = m - 1; tm.tm_mday = d;
    tm.tm_hour = h; tm.tm_min = i; tm.tm_sec = s; tm.tm_isdst = -1;
    return std::mktime(&tm);
}
int main()
{
    setenv("TZ", "UTC", 1); tzset();
    melonDS::NDSArgs args{};
    args.JIT = std::nullopt;
    auto nds = std::make_unique<melonDS::NDS>(std::move(args), nullptr);
    MelonDSAndroid::ConfigurationBoundary config{false};
    MelonDSAndroid::MelonInstance instance{nds.get(), &config};
    auto now = std::time(nullptr);
    set(nds->RTC, now - 3600);
    instance.onResumed();
    expect(read(nds->RTC) == now - 3600, "disabled sync preserves guest clock after resume");
    config.rtcSyncToHost = true;
    const auto before = std::time(nullptr);
    instance.onResumed();
    const auto after = std::time(nullptr);
    const auto synced = read(nds->RTC);
    expect(synced >= before && synced <= after, "resume catches real RTC up to host time");
    for (int i = 0; i < 20; ++i) instance.onResumed();
    expect(read(nds->RTC) == synced, "repeated resume does not restamp a synchronized clock");
    now = std::time(nullptr);
    set(nds->RTC, now + 3600);
    instance.onResumed();
    expect(read(nds->RTC) == now + 3600, "ahead guest clock never moves backward");
    set(nds->RTC, now - 5);
    instance.onResumed();
    expect(read(nds->RTC) == now - 5, "small drift preserves guest time");
    set(nds->RTC, now - 600);
    instance.onResumed();
    expect(read(nds->RTC) >= now, "a second pause/resume catches up again");
    return failures ? 1 : 0;
}
