#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
充电桩异常检测脚本 (预测性维护)

本脚本模拟从系统日志/数据库中读取历史充电数据（电压、电流、SOC），
使用统计阈值法（均值 ± 2σ 标准差）检测异常波动。
当检测到超出正常范围的数据点时，输出告警信息并标记可能存在故障的充电桩。

使用方法:
    python3 scripts/anomaly_detector.py

技术原理:
    对于每个充电桩的时序数据（电压/电流），计算其 均值(μ) 和 标准差(σ)。
    如果某个数据点超出 [μ - 2σ, μ + 2σ] 的范围，则判定为异常。
    异常比例超过 10% 的充电桩将被标记为"需要维护"。
"""

import random
import statistics
from datetime import datetime, timedelta


def generate_mock_data(pile_id: str, hours: int = 24) -> list:
    """
    生成模拟的充电桩运行数据
    
    正常充电桩：电压稳定在 220V ± 5V，电流稳定在 32A ± 2A
    故障充电桩：会出现电压骤降、电流突增等异常
    """
    data_points = []
    is_faulty = pile_id in ["PILE-003", "PILE-007"]  # 模拟两台故障桩
    
    for i in range(hours * 6):  # 每10分钟一条数据
        timestamp = datetime.now() - timedelta(hours=hours) + timedelta(minutes=i * 10)
        
        # 正常数据：均匀分布在标准范围内
        voltage = random.gauss(220, 3)
        current = random.gauss(32, 1.5)
        
        # 故障桩：随机注入异常值
        if is_faulty and random.random() < 0.15:  # 15% 的概率出现异常
            if random.random() < 0.5:
                voltage = random.uniform(180, 195)  # 电压骤降
            else:
                current = random.uniform(45, 60)     # 电流异常拉升
        
        data_points.append({
            "timestamp": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
            "pile_id": pile_id,
            "voltage": round(voltage, 2),
            "current": round(current, 2),
            "soc": min(100, round(random.uniform(20, 95), 1))
        })
    
    return data_points


def detect_anomalies(data_points: list, field: str, threshold_sigma: float = 2.0) -> dict:
    """
    使用 μ ± nσ 统计阈值法检测异常数据点
    
    参数:
        data_points: 数据列表
        field: 要检测的字段名 ('voltage' 或 'current')
        threshold_sigma: 标准差倍数阈值（默认 2.0）
    
    返回:
        包含均值、标准差、异常点列表的字典
    """
    values = [dp[field] for dp in data_points]
    
    mean = statistics.mean(values)
    stdev = statistics.stdev(values) if len(values) > 1 else 0
    
    lower_bound = mean - threshold_sigma * stdev
    upper_bound = mean + threshold_sigma * stdev
    
    anomalies = []
    for dp in data_points:
        val = dp[field]
        if val < lower_bound or val > upper_bound:
            anomalies.append({
                "timestamp": dp["timestamp"],
                "value": val,
                "deviation": round(abs(val - mean) / stdev if stdev > 0 else 0, 2)
            })
    
    return {
        "field": field,
        "mean": round(mean, 2),
        "stdev": round(stdev, 2),
        "range": f"[{round(lower_bound, 2)}, {round(upper_bound, 2)}]",
        "total_points": len(data_points),
        "anomaly_count": len(anomalies),
        "anomaly_rate": round(len(anomalies) / len(data_points) * 100, 1),
        "anomalies": anomalies
    }


def main():
    print("=" * 70)
    print("  共享充电桩异常检测系统 (预测性维护)")
    print(f"  扫描时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    # 定义要检测的充电桩编号
    pile_ids = ["PILE-001", "PILE-002", "PILE-003", "PILE-004", 
                "PILE-005", "PILE-006", "PILE-007", "PILE-008"]
    
    alert_piles = []  # 需要告警的充电桩
    
    for pile_id in pile_ids:
        print(f"\n{'─' * 50}")
        print(f"正在分析充电桩 [{pile_id}] ...")
        
        # 1. 获取该桩过去 24 小时的数据
        data = generate_mock_data(pile_id, hours=24)
        
        # 2. 分别检测电压和电流的异常
        voltage_result = detect_anomalies(data, "voltage")
        current_result = detect_anomalies(data, "current")
        
        # 3. 输出检测结果
        print(f"  📊 电压分析: 均值={voltage_result['mean']}V, "
              f"标准差={voltage_result['stdev']}V, "
              f"正常范围={voltage_result['range']}V")
        print(f"     异常数据点: {voltage_result['anomaly_count']}/{voltage_result['total_points']} "
              f"({voltage_result['anomaly_rate']}%)")
        
        print(f"  📊 电流分析: 均值={current_result['mean']}A, "
              f"标准差={current_result['stdev']}A, "
              f"正常范围={current_result['range']}A")
        print(f"     异常数据点: {current_result['anomaly_count']}/{current_result['total_points']} "
              f"({current_result['anomaly_rate']}%)")
        
        # 4. 判定是否需要告警（异常率超过 10%）
        needs_alert = (voltage_result['anomaly_rate'] > 10 or 
                       current_result['anomaly_rate'] > 10)
        
        if needs_alert:
            status = "⚠️  【告警】检测到异常波动，建议立即排查！"
            alert_piles.append(pile_id)
        else:
            status = "✅  运行正常"
        
        print(f"  状态: {status}")
    
    # 5. 输出汇总报告
    print(f"\n{'=' * 70}")
    print("  汇总报告")
    print(f"{'=' * 70}")
    print(f"  检测充电桩总数: {len(pile_ids)}")
    print(f"  正常充电桩: {len(pile_ids) - len(alert_piles)} 台")
    print(f"  异常充电桩: {len(alert_piles)} 台")
    
    if alert_piles:
        print(f"\n  🔴 以下充电桩需要维护人员现场检查:")
        for pid in alert_piles:
            print(f"     → {pid}")
        print(f"\n  建议: 已自动在运维系统中创建工单，请 24 小时内安排技术人员前往检修。")
    else:
        print(f"\n  🟢 所有充电桩运行状态良好，无需干预。")
    
    print(f"\n{'=' * 70}")


if __name__ == "__main__":
    main()
