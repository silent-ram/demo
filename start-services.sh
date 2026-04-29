#!/bin/bash
# 工业设备故障预警系统 - 环境变量配置与启动脚本
# 请将本文件中的密码替换为真实值，并确保 .gitignore 已排除此文件

export MYSQL_URL="jdbc:mysql://localhost:3306/fault_warning?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai"
export MYSQL_USER="root"
export MYSQL_PASSWORD=""

export JWT_SECRET=""
export JWT_EXPIRATION="86400000"

export INFLUXDB_URL="http://localhost:8086"
export INFLUXDB_TOKEN=""
export INFLUXDB_ORG="cy"
export INFLUXDB_BUCKET="sensor_data"

export ML_SERVICE_URL="http://localhost:5000"
export ML_API_KEY=""

# 检查敏感配置
if [ -z "$MYSQL_PASSWORD" ]; then
    echo "警告: MYSQL_PASSWORD 未设置"
fi
if [ -z "$JWT_SECRET" ]; then
    echo "错误: JWT_SECRET 必须设置"
    exit 1
fi

echo "环境变量配置完成，启动服务..."
