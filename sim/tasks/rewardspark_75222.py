# -*- coding: utf-8 -*-
"""DSL task: singingfiles#75222 -> rewardspark $1000 SOI (tonney QA data)."""
TASK = {
    "name": "Singingfiles #75222 rewardspark",
    "proxy": {"file": "/tmp/proxy_ordered.json", "index": 9},
    "steps": [
        {"do": "goto",
         "url": "https://singingfiles.com/show.php?l=0&u=2214761&id=75222",
         "as": "landing"},
        {"do": "submit",
         "from": "landing",
         "form_action_contains": "cash1000",
         "fields": {
             "first_name": "tonney", "last_name": "kamlo",
             "email": "tonney.kamlo.test@singingfiles.com",
             "address1": "123 Test Delete Street", "address2": "",
             "city": "New York", "state": "NY", "zip": "10001",
             "phone": "91276465465", "dob": "12/25/2002", "gender": "m",
             "aid": "16473", "pid": "1088",
             "sid1": "100_2214761", "sid2": "",
         },
         "as": "step1"},
        {"do": "submit",
         "from": "step1",
         "form_id": "formSubmit",
         "fields": {
             "email": "tonney.kamlo.test@singingfiles.com",
             "first_name": "tonney", "last_name": "kamlo",
             "zip": "10001", "city": "New York", "phone": "91276465465",
             "tcpaconsent": "on", "consent": "on",
         },
         "checks": ["chktcpa", "chkterms"],
         "as": "step2"},
        {"do": "verify", "type": "rss", "ip_from": "proxy"},
    ],
}
