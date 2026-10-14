#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""重新登录后核对真实订单、票码、资金和核销记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,json
from http_client import Client,ROOT,env

def run(state,base=None):
    data=json.loads(state.read_text());url=base or data['base'];cfg=env();admin=Client(url);admin.login(cfg.get('ADMIN_USERNAME','admin'),cfg['ADMIN_PASSWORD']);buyer=Client(url);u=data['users']['buyer'];buyer.login(u['username'],u['password'])
    expected={'admitted':('PAID','CHECKED_IN'),'refunded':('REFUNDED','VOID'),'replacement':('PAID',None),'preview':('PAID',None),'cancelled':('EXPIRED',None)}
    if 'browser' in data['orders']: expected['browser']=('PAID',data.get('browserTicketState'))
    for key,(status,ticket) in expected.items():
        row=buyer.request('/wallet/'+data['orders'][key]);assert row['state']==status,(key,'wrong order state')
        if ticket: assert row['ticketState']==ticket
        if key=='preview':assert row['code']==data['previewCode']
    reports=admin.request('/reports');row=next(r for r in reports if r['id']==data['eventId'] and r['mode']=='DEMO');assert row==data['report']
    records=admin.request('/events/'+data['eventId']+'/operations');assert records['summary']['admissions']>=1 and records['summary']['occupied']==data.get('occupied',3)
    print('Persisted orders, stable ticket code, report and admission records verified:',len(admin.checks)+len(buyer.checks),'requests. No credentials printed.')

if __name__=='__main__':
    from pathlib import Path
    p=argparse.ArgumentParser();p.add_argument('--state',type=Path,default=ROOT/'output/ticketdesk-quality-state.json');p.add_argument('--base');a=p.parse_args();run(a.state,a.base)
