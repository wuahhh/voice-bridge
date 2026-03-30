"""
Voice Bridge Server - Mac端语音控制服务
通过WebSocket接收Android设备的语音指令，调用OpenClaw执行
"""

import asyncio
import json
import logging
import subprocess
import sys
from datetime import datetime
from typing import Optional, Set

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.responses import JSONResponse
import uvicorn
from pydantic import BaseModel

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# FastAPI应用
app = FastAPI(title="Voice Bridge Server", version="2.0.0")

# 存储活跃的WebSocket连接
active_connections: Set[WebSocket] = set()

# 服务状态
server_status = {
    "started_at": None,
    "total_commands": 0,
    "successful_commands": 0,
    "failed_commands": 0,
    "last_command": None,
    "connected_clients": 0
}


class CommandRequest(BaseModel):
    """命令请求模型"""
    command: str
    request_id: Optional[str] = None


def execute_openclaw(command: str) -> tuple[bool, str]:
    """
    执行OpenClaw agent命令
    """
    try:
        logger.info(f"执行OpenClaw: {command}")
        
        # 调用OpenClaw agent
        result = subprocess.run(
            ["openclaw", "agent", "--agent", "main", "-m", command],
            capture_output=True,
            text=True,
            timeout=120,  # 2分钟超时
            encoding='utf-8'
        )
        
        if result.returncode == 0:
            output = result.stdout.strip()
            # 简化输出，取前200字符作为摘要
            summary = output[:300] if len(output) > 300 else output
            logger.info(f"执行成功: {summary[:100]}...")
            return True, summary
        else:
            error = result.stderr.strip() or "执行失败"
            logger.error(f"执行失败: {error}")
            return False, error
            
    except subprocess.TimeoutExpired:
        logger.error("执行超时")
        return False, "任务执行超时，请重试"
    except FileNotFoundError:
        logger.error("OpenClaw未找到")
        return False, "OpenClaw未安装"
    except Exception as e:
        logger.error(f"执行异常: {str(e)}")
        return False, f"执行异常: {str(e)}"


@app.on_event("startup")
async def startup_event():
    """服务启动事件"""
    server_status["started_at"] = datetime.now().isoformat()
    logger.info("=" * 50)
    logger.info("Voice Bridge Server v2.0 已启动")
    logger.info("支持完整OpenClaw集成")
    logger.info("=" * 50)


@app.get("/health")
async def health_check():
    """健康检查端点"""
    return JSONResponse({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "connected_clients": len(active_connections),
        "total_commands": server_status["total_commands"]
    })


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """
    WebSocket端点 - 处理Android设备的语音指令
    """
    await websocket.accept()
    active_connections.add(websocket)
    client_id = id(websocket)
    
    logger.info(f"客户端 {client_id} 已连接")
    
    try:
        while True:
            message = await websocket.receive_text()
            
            try:
                data = json.loads(message)
                command = data.get("command", "").strip()
                request_id = data.get("request_id")
                need_tts = data.get("tts", True)
                
                if not command:
                    await websocket.send_json({
                        "type": "error",
                        "success": False,
                        "error": "空命令",
                        "request_id": request_id
                    })
                    continue
                
                logger.info(f"[{client_id}] 指令: {command}")
                server_status["total_commands"] += 1
                
                # 执行OpenClaw
                success, result = execute_openclaw(command)
                
                if success:
                    server_status["successful_commands"] += 1
                else:
                    server_status["failed_commands"] += 1
                
                # 构建响应
                response = {
                    "type": "command_result",
                    "success": success,
                    "result": result,
                    "timestamp": datetime.now().isoformat(),
                    "request_id": request_id
                }
                
                await websocket.send_json(response)
                logger.info(f"[{client_id}] 响应已发送")
                
            except json.JSONDecodeError:
                await websocket.send_json({
                    "type": "error",
                    "success": False,
                    "error": "无效的JSON格式"
                })
            except Exception as e:
                logger.error(f"处理错误: {str(e)}")
                await websocket.send_json({
                    "type": "error",
                    "success": False,
                    "error": f"服务器错误: {str(e)}"
                })
                
    except WebSocketDisconnect:
        logger.info(f"客户端 {client_id} 断开")
    finally:
        active_connections.discard(websocket)


if __name__ == "__main__":
    import socket
    
    # 获取本机IP
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
    except:
        local_ip = "127.0.0.1"
    
    print("\n" + "=" * 60)
    print("🎙️  Voice Bridge Server v2.0")
    print("=" * 60)
    print(f"📡  服务地址: http://{local_ip}:8765")
    print(f"🔗  WebSocket: ws://{local_ip}:8765/ws")
    print("=" * 60 + "\n")
    
    uvicorn.run("main:app", host="0.0.0.0", port=8765, log_level="info")
