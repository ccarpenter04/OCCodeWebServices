# OCCode Web Services #
This repository contains files used for running the OCCode Web Service through a bot on a given platform. This service uses a RESTful api to fulfill requests and handles then with descriptive response code where appropriate.

# Running #
Details to setup this project can be found at https://occode.io/dash/api. 

# API #
API can be found at https://occode.io/dash/api.

The Response codes
=============
* 200
"[OK] Everything works as expected."
* 400
"[Invalid format] Invalid body format."
* 401 
"[Unauthorized] Unauthorized access. Probably invalid token."
* 403
"[Forbidden] It is forbidden for you to do that."
* 429
"[Too Many Requests] You posted that too many times."
* 503
"[Service Unavailable] Server is currently not accepting any requests. Probably under maintenance."
