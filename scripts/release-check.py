#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核查发布资产、署名、双语及配置；不输出真实秘密。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib,re,subprocess,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
expected={'logo.jpg':'90bf87ff294f5ee8cdaef4b10155139173d97d93a88d7ea9679b9721ba05c962','wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
for name,digest in expected.items():assert hashlib.sha256((ROOT/'frontend/public/brand'/name).read_bytes()).hexdigest()==digest,name
cn=(ROOT/'README.md').read_text();en=(ROOT/'README.en.md').read_text();license=(ROOT/'LICENSE').read_text()
assert '上海如静知华信息科技有限公司' in cn and 'Shanghai Rujing Zhihua Information Technology Co., Ltd.' in en
assert 'README.en.md' in cn and 'README.md' in en
assert 'wechat-zhuatech.png' in cn and 'wechat-zhuatech2.png' in cn and 'wechat-' not in en
for text in [cn,en]:assert 'https://www.zhuatech.cn/' in text
for contact in ['han@zhuatech.cn','jack@zhuatech.cn','https://wa.me/8617521234993']:assert contact in en
assert '不得商用' in cn and 'non-commercial' in en.lower() and '未经' in license
for name in ['login','events','seats','wallet','organizer','checkin','reports','accounts','roles','settings','mobile','english']:
 data=(ROOT/'docs/screenshots'/f'{name}.jpg').read_bytes();assert data[:2]==b'\xff\xd8' and len(data)>10000,name
for name in ['README.md','README.en.md']:
 text=(ROOT/name).read_text()
 for link in re.findall(r'\]\(([^)]+)\)|(?:src|href)="([^"]+)"',text):
  target=next(x for x in link if x)
  if not target.startswith(('https://','http://','mailto:','#')):assert (ROOT/target).exists(),target
tracked=subprocess.check_output(['git','ls-files','--cached'],cwd=ROOT,text=True).splitlines()
assert tracked,'Stage the reviewed source before release checks'
assert all('frontend/public/brand/'+name in tracked for name in expected)
secrets=[]
if (ROOT/'.env').exists():
 for line in (ROOT/'.env').read_text().splitlines():
  if '=' in line and not line.startswith('#'):
   key,value=line.split('=',1)
   if value and ('PASSWORD' in key or 'KEY' in key or 'SECRET' in key):secrets.append(value.encode())
for name in tracked:
 assert not name.startswith(('output/','node_modules/','backend/target/')) and Path(name).name!='.env',name
 data=(ROOT/name).read_bytes();assert not any(value in data for value in secrets),name
 if name.endswith(('.java','.vue','.py','.css','.sql','.yaml','.yml','.js')):
  text=data.decode();assert 'zhuatech.cn' in text[:600] and 'zhuatech2' in text[:600],name
for name in ['backend/Dockerfile','frontend/Dockerfile']:
 text=(ROOT/name).read_text();assert 'skipTests' not in text and 'maven.test.skip' not in text
subprocess.run(['git','diff','--check'],cwd=ROOT,check=True)
subprocess.run(['git','diff','--cached','--check'],cwd=ROOT,check=True)
json.loads((ROOT/'frontend/package.json').read_text());json.loads((ROOT/'frontend/package-lock.json').read_text())
print('Release assets, original QR files, bilingual contacts, license, links, branding, staged secrets and Git whitespace checks passed.')
