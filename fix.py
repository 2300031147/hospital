import urllib.request
import json
import re

url1 = "https://api.github.com/repos/2300031147/hospital/pulls/comments/3231449128"
url2 = "https://api.github.com/repos/2300031147/hospital/pulls/comments/3231449118"

req1 = urllib.request.Request(url1, headers={'Accept': 'application/vnd.github.v3+json'})
req2 = urllib.request.Request(url2, headers={'Accept': 'application/vnd.github.v3+json'})

try:
    with urllib.request.urlopen(req1) as response:
        print("Comment 1:", json.loads(response.read().decode())['body'])
except Exception as e:
    print("Error 1:", e)

try:
    with urllib.request.urlopen(req2) as response:
        print("Comment 2:", json.loads(response.read().decode())['body'])
except Exception as e:
    print("Error 2:", e)
