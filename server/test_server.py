#!/usr/bin/env python3
"""
服务端测试脚本 - 无需App即可测试
"""
import sys
import json
import time
import websocket
import threading

def test_websocket():
    """测试WebSocket连接"""
    print("🧪 测试 WebSocket 连接...")
    try:
        ws = websocket.create_connection("ws://192.168.1.12:8765/ws", timeout=5)
        print("✅ WebSocket 连接成功")
        
        # 发送测试命令
        test_cmd = {
            "type": "command",
            "command": "你好，这是一个测试",
            "request_id": "test-001",
            "tts": False
        }
        ws.send(json.dumps(test_cmd))
        print(f"📤 发送: {test_cmd['command']}")
        
        # 接收响应
        response = ws.recv()
        data = json.loads(response)
        print(f"📥 收到: {data}")
        
        if data.get('success'):
            print("✅ 服务端执行成功")
        else:
            print(f"❌ 服务端执行失败: {data.get('result', '未知错误')}")
        
        ws.close()
        return True
        
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_http():
    """测试HTTP接口"""
    print("\n🧪 测试 HTTP 健康检查...")
    try:
        import urllib.request
        response = urllib.request.urlopen("http://192.168.1.12:8765/health", timeout=5)
        data = json.loads(response.read())
        print(f"✅ HTTP 正常: {data}")
        return True
    except Exception as e:
        print(f"❌ HTTP 测试失败: {e}")
        return False

if __name__ == "__main__":
    print("="*50)
    print("🎙️ Voice Bridge 服务端测试")
    print("="*50)
    
    http_ok = test_http()
    ws_ok = test_websocket()
    
    print("\n" + "="*50)
    if http_ok and ws_ok:
        print("✅ 所有测试通过，服务端正常")
    else:
        print("❌ 测试失败，请检查服务端")
    print("="*50)
