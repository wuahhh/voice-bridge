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
app = FastAPI(title="Voice Bridge Server", version="1.0.0")

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


class CommandResponse(BaseModel):
    """命令响应模型"""
    success: bool
    result: str
    audio_url: Optional[str] = None
    timestamp: str
    request_id: Optional[str] = None


def execute_command(command: str) -> tuple[bool, str]:
    """
    执行命令 - 简化版，直接返回收到的指令
    """
    try:
        clean_command = command.strip()
        logger.info(f"收到指令: {clean_command}")
        
        # 这里可以接入OpenClaw或其他逻辑
        # 现在直接返回成功，方便调试
        result = f"收到指令: {clean_command}"
        
        return True, result
            
    except Exception as e:
        logger.error(f"执行异常: {str(e)}")
        return False, f"执行异常: {str(e)}"


def preprocess_command(command: str) -> str:
    """
    预处理语音命令，移除唤醒词和填充词
    """
    wake_words = ["小助手", "hey assistant", "嘿"]
    
    lower_cmd = command.lower().strip()
    
    for wake in wake_words:
        lower_cmd = lower_cmd.replace(wake.lower(), "")
    
    clean = lower_cmd.strip()
    return clean if clean else command


def text_to_speech(text: str) -> Optional[bytes]:
    """
    使用macOS say命令将文本转为语音
    """
    try:
        import tempfile
        import os
        
        with tempfile.NamedTemporaryFile(suffix=".aiff", delete=False) as f:
            temp_path = f.name
        
        voice = "Ting-Ting" if any('\u4e00' <= c <= '\u9fff' for c in text) else "Samantha"
        
        subprocess.run(
            ["say", "-v", voice, "-o", temp_path, text[:200]],
            check=True,
            timeout=30
        )
        
        with open(temp_path, "rb") as f:
            audio_data = f.read()
        
        os.unlink(temp_path)
        return audio_data
        
    except Exception as e:
        logger.error(f"TTS失败: {str(e)}")
        return None


@app.on_event("startup")
async def startup_event():
    """服务启动事件"""
    server_status["started_at"] = datetime.now().isoformat()
    logger.info("=" * 50)
    logger.info("Voice Bridge Server 已启动")
    logger.info("等待Android设备连接...")
    logger.info("=" * 50)


@app.on_event("shutdown")
async def shutdown_event():
    """服务关闭事件"""
    logger.info("Voice Bridge Server 正在关闭...")


@app.get("/health")
async def health_check():
    """健康检查端点"""
    return JSONResponse({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "uptime": server_status["started_at"],
        "connected_clients": len(active_connections),
        "total_commands": server_status["total_commands"]
    })


@app.get("/status")
async def get_status():
    """获取服务状态"""
    return JSONResponse({
        **server_status,
        "connected_clients": len(active_connections)
    })


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """
    WebSocket端点 - 处理Android设备的语音指令
    """
    await websocket.accept()
    active_connections.add(websocket)
    client_id = id(websocket)
    
    logger.info(f"客户端 {client_id} 已连接 | 当前连接数: {len(active_connections)}")
    
    try:
        while True:
            message = await websocket.receive_text()
            
            try:
                data = json.loads(message)
                command = data.get("command", "").strip()
                request_id = data.get("request_id")
                
                if not command:
                    await send_error_response(websocket, "空命令", request_id)
                    continue
                
                logger.info(f"收到指令 [{client_id}]: {command}")
                server_status["last_command"] = {
                    "command": command,
                    "timestamp": datetime.now().isoformat(),
                    "client_id": client_id
                }
                
                # 执行命令
                success, result = execute_command(command)
                server_status["total_commands"] += 1
                
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
                logger.info(f"响应已发送 [{client_id}]: {result}")
                
            except json.JSONDecodeError:
                await send_error_response(websocket, "无效的JSON格式", None)
            except Exception as e:
                logger.error(f"处理消息时出错: {str(e)}")
                await send_error_response(websocket, f"服务器错误: {str(e)}", None)
                
    except WebSocketDisconnect:
        logger.info(f"客户端 {client_id} 断开连接")
    except Exception as e:
        logger.error(f"WebSocket错误 [{client_id}]: {str(e)}")
    finally:
        active_connections.discard(websocket)
        logger.info(f"客户端 {client_id} 已清理 | 当前连接数: {len(active_connections)}")


async def send_error_response(websocket, error_msg: str, request_id: Optional[str]):
    """发送错误响应"""
    await websocket.send_json({
        "type": "error",
        "success": False,
        "error": error_msg,
        "timestamp": datetime.now().isoformat(),
        "request_id": request_id
    })


@app.post("/api/command")
async def http_command(request: CommandRequest):
    """
    HTTP API端点 - 用于测试
    """
    success, result = execute_command(request.command)
    
    return CommandResponse(
        success=success,
        result=result,
        timestamp=datetime.now().isoformat(),
        request_id=request.request_id
    )


def get_local_ip() -> str:
    """获取本地IP地址"""
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Voice Bridge Server")
    parser.add_argument("--host", default="0.0.0.0", help="绑定地址")
    parser.add_argument("--port", type=int, default=8765, help="端口")
    parser.add_argument("--log-level", default="info", help="日志级别")
    
    args = parser.parse_args()
    
    local_ip = get_local_ip()
    
    print("\n" + "=" * 60)
    print("🎙️  Voice Bridge Server")
    print("=" * 60)
    print(f"📡  服务地址: http://{local_ip}:{args.port}")
    print(f"🔗  WebSocket: ws://{local_ip}:{args.port}/ws")
    print(f"💓  健康检查: http://{local_ip}:{args.port}/health")
    print("=" * 60 + "\n")
    
    uvicorn.run(
        "main:app",
        host=args.host,
        port=args.port,
        log_level=args.log_level,
        reload=False
    )
