#!/usr/bin/env python3
"""为 lifeline-docking 的管理页面 + /admin/** 加 HTTP Basic 认证。

做法：重写 443 server 块内的 lifeline 段
  - 三个管理页面（credentials/debug/records.html）和 /admin/**：加 auth_basic
  - 首页、知识库、静态资源：不加认证，保持开放
"""
import io
import sys

PATH = '/etc/nginx/sites-available/ai.mockr.com.cn'
MARK = '# lifeline-docking'

src = io.open(PATH, encoding='utf-8').read()
lines = src.split('\n')

# --- 定位 443 server 块 ---
s443 = next((i for i, ln in enumerate(lines) if 'listen 443 ssl' in ln), None)
if s443 is None:
    raise SystemExit('443 server block not found')

block_start = None
for j in range(s443, -1, -1):
    s = lines[j].strip()
    if s.startswith('server') and s.endswith('{'):
        block_start = j
        break

depth = 0
block_end = None
for j in range(block_start, len(lines)):
    stripped = lines[j].strip()
    if stripped.startswith('#'):
        continue
    code_part = lines[j].split('#')[0] if '#' in lines[j] else lines[j]
    depth += code_part.count('{') - code_part.count('}')
    if depth == 0 and j > block_start:
        block_end = j
        break

# --- 删除块内旧 lifeline 段 ---
body = lines[block_start + 1:block_end]
keep = []
skipping = False
for ln in body:
    if MARK in ln:
        skipping = True
        while keep and keep[-1].strip().startswith('# ====='):
            keep.pop()
        while keep and keep[-1].strip() == '':
            keep.pop()
        continue
    if skipping:
        continue
    keep.append(ln)
while keep and keep[-1].strip() == '':
    keep.pop()

BLOCK = '''
    # ==================================================================
    # lifeline-docking · https://ai.mockr.com.cn/lifeline-docking
    #
    # 2026-09-24 首版；同日调整：管理页面 + /admin/** 加 HTTP Basic 认证
    #
    # 免认证开放：首页 /lifeline-docking/、知识库 /kb/index.html、静态资源
    # 需认证访问：三个管理页面 + /admin/**（口令文件 /etc/nginx/lifeline.htpasswd）
    # 调用方入口（限流 10r/s，免认证，靠签名鉴权）：
    #       POST /lifeline-docking/data-docking-api/token
    #       POST /lifeline-docking/data-docking-api/execute/api
    #
    # 回滚：删除本段 + rm /etc/nginx/conf.d/lifeline-ratelimit.conf + reload
    # ==================================================================

    auth_basic off;

    # --- 需认证：管理页面（能增删改凭证、往老平台发数据、读上收记录）---
    location = /lifeline-docking/credentials.html {
        auth_basic "lifeline admin";
        auth_basic_user_file /etc/nginx/lifeline.htpasswd;
        proxy_pass http://127.0.0.1:3110/credentials.html;
        include /etc/nginx/snippets/lifeline-proxy.conf;
    }
    location = /lifeline-docking/debug.html {
        auth_basic "lifeline admin";
        auth_basic_user_file /etc/nginx/lifeline.htpasswd;
        proxy_pass http://127.0.0.1:3110/debug.html;
        include /etc/nginx/snippets/lifeline-proxy.conf;
    }
    location = /lifeline-docking/records.html {
        auth_basic "lifeline admin";
        auth_basic_user_file /etc/nginx/lifeline.htpasswd;
        proxy_pass http://127.0.0.1:3110/records.html;
        include /etc/nginx/snippets/lifeline-proxy.conf;
    }

    # --- 需认证：/admin/** 全部接口 ---
    location /lifeline-docking/admin/ {
        auth_basic "lifeline admin";
        auth_basic_user_file /etc/nginx/lifeline.htpasswd;
        proxy_pass http://127.0.0.1:3110/admin/;
        include /etc/nginx/snippets/lifeline-proxy.conf;
    }

    # --- 调用方入口：取令牌（免认证，靠报文签名）---
    location = /lifeline-docking/data-docking-api/token {
        proxy_pass http://127.0.0.1:3110/data-docking-api/token;
        include /etc/nginx/snippets/lifeline-proxy.conf;
        client_max_body_size 2m;
        limit_req zone=lifeline_api burst=20 nodelay;
        limit_req_status 429;
        access_log /var/log/nginx/lifeline-api.access.log;
    }

    # --- 调用方入口：数据推送（免认证，靠报文签名）---
    location = /lifeline-docking/data-docking-api/execute/api {
        proxy_pass http://127.0.0.1:3110/data-docking-api/execute/api;
        include /etc/nginx/snippets/lifeline-proxy.conf;
        client_max_body_size 2m;
        limit_req zone=lifeline_api burst=20 nodelay;
        limit_req_status 429;
        access_log /var/log/nginx/lifeline-api.access.log;
    }

    # --- 不带斜杠：301 到带斜杠，避免漏进 NewAPI 的 location / ---
    location = /lifeline-docking { return 301 /lifeline-docking/; }

    # --- 知识库：应用内部 302 到绝对路径 /kb/index.html，挂前缀后会跳飞，
    #     故在 Nginx 层直接改写成带前缀的静态路径 ---
    location = /lifeline-docking/kb { return 301 /lifeline-docking/kb/index.html; }
    location = /lifeline-docking/kb/ { return 301 /lifeline-docking/kb/index.html; }

    # --- 免认证浏览：首页 + 知识库（相对路径资源，代理时去掉前缀）---
    location /lifeline-docking/ {
        proxy_pass http://127.0.0.1:3110/;
        include /etc/nginx/snippets/lifeline-proxy.conf;
    }
'''

new_body = keep + BLOCK.rstrip('\n').split('\n')
out = lines[:block_start + 1] + new_body + [''] + lines[block_end:]
io.open(PATH, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('patched: removed %d, inserted %d lines' % (len(body) - len(keep), len(new_body) - len(keep)))
