#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅在独立DEMO空库创建QA流程；不执行真实收款或邮件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,json,os,secrets,time,uuid
from datetime import datetime,timezone,timedelta
from pathlib import Path
from http_client import Client,ROOT,env

def run(base,state):
    config=env();admin=Client(base);anon=Client(base)
    assert anon.request('/public/config')['paymentMode']=='DEMO','QA requires DEMO mode'
    profile=admin.login(config.get('ADMIN_USERNAME','admin'),config['ADMIN_PASSWORD'])
    options=admin.request('/admin-options');roles=options['roles'];department=options['departments'][0]['id']
    assert len(roles)>=4
    password=lambda:'Aa9'+secrets.token_hex(16)
    users={};suffix=uuid.uuid4().hex[:8]
    for kind,scope,permission in [('buyer','CUSTOMER','portal'),('other','CUSTOMER','portal'),('gate','DEPARTMENT','checkin'),('unassigned','DEPARTMENT','checkin')]:
        role=next(r for r in roles if r['scope']==scope and permission in r['permissions'])
        users[kind]={'username':'qa-'+kind+'-'+suffix,'password':password()}
        a=admin.request('/admin/users',{'username':users[kind]['username'],'displayName':'QA '+kind,'email':'qa-'+kind+'@example.invalid','password':users[kind]['password'],'roleId':role['id'],'departmentId':department,'enabled':True})
        assert 'passwordHash' not in a and 'password' not in a
        users[kind]['id']=a['id']
    clients={kind:Client(base) for kind in users}
    for kind,c in clients.items(): c.login(users[kind]['username'],users[kind]['password'])
    buyer,other,gate,unassigned=(clients[k] for k in ['buyer','other','gate','unassigned'])
    now=datetime.now(timezone.utc);iso=lambda delta:(now+timedelta(seconds=delta)).isoformat()
    event={'name':'QA 票务流程验收 '+suffix,'nameEn':'QA Ticketing Acceptance '+suffix,'venue':'QA 测试会场','venueEn':'QA Test Hall','description':'仅用于验证的活动资料。','descriptionEn':'Marked QA fixture for workflow verification.','category':'TALK','currency':'CNY','departmentId':department,'staff':[users['gate']['id']],'state':'DRAFT','startsAt':iso(14400),'saleStartsAt':iso(-3600),'saleEndsAt':iso(12600),'refundEndsAt':iso(12000)}
    event_id=admin.request('/events',event)['id'];event['id']=event_id
    admin.request('/events/'+event_id+'/types',{'name':'QA 标准票','nameEn':'QA Standard','prefix':'A-','price':2000,'capacity':200,'columns':10})
    event['version']=1;event['state']='PUBLISHED';admin.request('/events/'+event_id,event,method='PUT');event['version']=2
    public=anon.request('/public/events/'+event_id);assert len(public['seats'])==200 and 'staff' not in public and 'departmentId' not in public
    seats=public['seats'];assert all(set(s)=={'id','label','price','available'} for s in seats)
    reserve=lambda client,index:client.request('/events/'+event_id+'/reserve',{'seatId':seats[index]['id'],'requestId':str(uuid.uuid4())})
    first=reserve(buyer,0);order_id=first['id']
    other.request('/wallet/'+order_id,status=409);other.request('/wallet/'+order_id+'/checkout',{},status=409)
    buyer.request('/admin/users',status=403);anon.request('/wallet',status=401)
    admin.request('/events',event,status=403,csrf=False)
    buyer.request('/wallet/'+order_id+'/checkout',{});paid=buyer.request('/wallet/'+order_id+'/demo-pay',{})
    assert paid['state']=='PAID' and paid['ticketState']=='ISSUED' and paid['paymentMode']=='DEMO'
    assert paid['code']==buyer.request('/wallet/'+order_id)['code']
    unassigned.request('/events/'+event_id+'/check-in',{'code':paid['code']},status=403)
    assert gate.request('/events/'+event_id+'/check-in',{'code':paid['code']})['decision']=='ADMITTED'
    assert gate.request('/events/'+event_id+'/check-in',{'code':paid['code']})['decision']=='ALREADY_USED'
    buyer.request('/wallet/'+order_id+'/refund',{},status=409)
    cancelled=reserve(buyer,1);buyer.request('/wallet/'+cancelled['id']+'/cancel',{})
    assert anon.request('/public/events/'+event_id)['seats'][1]['available']
    refunded=reserve(buyer,2);buyer.request('/wallet/'+refunded['id']+'/checkout',{});old=buyer.request('/wallet/'+refunded['id']+'/demo-pay',{});buyer.request('/wallet/'+refunded['id']+'/refund',{})
    assert gate.request('/events/'+event_id+'/check-in',{'code':old['code']})['decision']=='VOID'
    for _ in range(35):
        current=buyer.request('/wallet/'+refunded['id'])
        if current['state']=='REFUNDED':break
        time.sleep(1)
    assert current['state']=='REFUNDED','Refund queue did not complete'
    assert anon.request('/public/events/'+event_id)['seats'][2]['available']
    replacement=reserve(buyer,2);buyer.request('/wallet/'+replacement['id']+'/checkout',{});replacement=buyer.request('/wallet/'+replacement['id']+'/demo-pay',{});assert old['code']!=replacement['code']
    assert gate.request('/events/'+event_id+'/check-in',{'code':old['code']})['decision']=='VOID'
    # A second valid ticket remains for UI image decoding and mobile inspection.
    preview=reserve(buyer,3);buyer.request('/wallet/'+preview['id']+'/checkout',{});preview=buyer.request('/wallet/'+preview['id']+'/demo-pay',{})
    summary=admin.request('/events/'+event_id+'/operations');assert 'td1.' not in json.dumps(summary)
    assert summary['summary']['admissions']==1 and summary['summary']['occupied']==3
    reports=admin.request('/reports');row=next(r for r in reports if r['id']==event_id and r['mode']=='DEMO');assert row['gross']==8000 and row['refunded']==2000 and row['net']==6000 and row['issued']==4 and row['checked']==1
    csv=admin.request('/reports.csv',binary=True);assert b'event,mode,currency' in csv and b'td1.' not in csv
    denied=dict(event,currency='USD');admin.request('/events/'+event_id,denied,status=409,method='PUT')
    admin.request('/events/'+event_id,dict(event,version=1),status=409,method='PUT')
    # Role revocation is immediate even with the old session.
    accounts=admin.request('/admin/users');bad=next(a for a in accounts if a['id']==users['unassigned']['id']);bad['enabled']=False;admin.request('/admin/users/'+str(bad['id']),bad,method='PUT');unassigned.request('/catalog',status=401)
    path=Path(state);path.parent.mkdir(parents=True,exist_ok=True);fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    safe={'base':base,'eventId':event_id,'eventName':event['name'],'users':users,'orders':{'admitted':paid['id'],'refunded':refunded['id'],'replacement':replacement['id'],'preview':preview['id'],'cancelled':cancelled['id']},'previewCode':preview['code'],'report':row,'checks':sum(len(c.checks) for c in [admin,anon,*clients.values()])}
    with os.fdopen(fd,'w') as f:json.dump(safe,f,ensure_ascii=False,indent=2)
    print('QA HTTP workflow passed:',safe['checks'],'requests; private state saved. No credentials printed.')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--base',default='http://127.0.0.1:8160');parser.add_argument('--state',default=str(ROOT/'output/ticketdesk-quality-state.json'));args=parser.parse_args();run(args.base,args.state)
