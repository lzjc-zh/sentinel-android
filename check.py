import re, json
import datetime as dt

log = '{"Result":{"Details":[{"Time":1787241600000,"ObjectName":"doubao-embedding-vision-251215","Usage":337600,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787241600000,"ObjectName":"auto","Usage":110730,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787241600000,"ObjectName":"minimax-m3","Usage":289,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"doubao-seed-evolving","Usage":133,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"deepseek-v4-pro-260425","Usage":7,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787587200000,"ObjectName":"minimax-m3","Usage":7284490,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"glm-5.3","Usage":1061266,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787846400000,"ObjectName":"minimax-m3","Usage":3831943,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"auto","Usage":48,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787500800000,"ObjectName":"minimax-m3","Usage":1900789,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"minimax-m3","Usage":5467876,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787414400000,"ObjectName":"deepseek-v4-flash-ga-260731","Usage":114,"Unit":"Tokens","BillingType":"WithinPlan"},{"Time":1787328000000,"ObjectName":"deepseek-v4-flash-ga-260731","Usage":574557,"Unit":"Tokens","BillingType":"WithinPlan"}]}}'
m = re.search(r'"Details":(\[.*?\])', log)
details = json.loads(m.group(1))

coding_models = {'minimax-m3','minimax-m2.7','glm-5.3','glm-5.2','doubao-seed-2.0-code','doubao-seed-code','doubao-seed-2.0-lite','doubao-seed-2.1-turbo','kimi-k2.6','kimi-k2.7-code','deepseek-v4-flash-ga-260731','deepseek-v4-pro-260425','doubao-seed-evolving'}

coding = [d for d in details if d['ObjectName'] in coding_models]
coding.sort(key=lambda x: x['Time'])

def ts_str(ts):
    return dt.datetime.fromtimestamp(ts/1000, tz=dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC (北京 %H:%M)')

first = coding[0]['Time']
last = coding[-1]['Time']
print(f'firstCodingCallTime: {ts_str(first)}')
print(f'lastCodingCallTime: {ts_str(last)}')

now = int(dt.datetime(2026, 8, 29, 15, 33, 0, tzinfo=dt.timezone(dt.timedelta(hours=8))).timestamp() * 1000)
print(f'now: {ts_str(now)}')

fiveHourMs = 5 * 60 * 60 * 1000
elapsed = now - first
periods = elapsed // fiveHourMs
currentPeriodStart = first + periods * fiveHourMs
currentPeriodEnd = currentPeriodStart + fiveHourMs
print(f'currentPeriodStart: {ts_str(currentPeriodStart)}')
print(f'currentPeriodEnd: {ts_str(currentPeriodEnd)}')
print(f'periods elapsed: {periods}')

in_window = [d for d in coding if currentPeriodStart <= d['Time'] < currentPeriodEnd]
print(f'current 5h count: {len(in_window)}')
for d in in_window:
    print(f"  - {d['ObjectName']} @ {ts_str(d['Time'])} tokens={d['Usage']}")

# 也考虑 auto + doubao-embedding-vision 是否算 Coding Plan
all_within_plan = [d for d in details if d['BillingType'] == 'WithinPlan']
print(f'\nall WithinPlan records: {len(all_within_plan)}')

weekStart = int(dt.datetime(2026, 8, 25, 0, 0, 0, tzinfo=dt.timezone(dt.timedelta(hours=8))).timestamp() * 1000)
monthStart = int(dt.datetime(2026, 8, 1, 0, 0, 0, tzinfo=dt.timezone(dt.timedelta(hours=8))).timestamp() * 1000)
print(f'week count: {sum(1 for d in coding if d["Time"] >= weekStart)}')
print(f'month count: {sum(1 for d in coding if d["Time"] >= monthStart)}')
