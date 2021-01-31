import requests, yaml, json

secretUsername = ""
secretPassword = ""
app_id = ""
app_secret = ""
app_name = ""

# You need to fill out 'redditUserNamePassword.yaml file with your information to get the authentication token.
# Follow this https://alpscode.com/blog/how-to-use-reddit-api/ for more information.
# 2FA Must be turned off before using this!!!!
with open('redditUsernamePassword.yaml') as file:
    options = yaml.load(file, yaml.FullLoader)
    secretUsername = options['username']
    secretPassword = options['password']
    app_id = options['app-id']
    app_secret = options['app-secret']
    app_name = options['app-name']

redirect = 'http://localhost:8080'

base_url = 'https://www.reddit.com/'
data = {'grant_type': 'password', 'username': secretUsername, 'password': secretPassword}
auth = requests.auth.HTTPBasicAuth(app_id, app_secret)
r = requests.post(base_url + 'api/v1/access_token',
                  data=data,
                  headers={'user-agent': app_name + ' by ' + secretUsername},
                  auth=auth)
redditResponse = r.json()

#Read options.json to jsonObject
#Update jsonObject with token
#Write back to file
data = ''
with open('resources/options.json', 'r') as jsonFile:
    data = json.load(jsonFile)
    for key in redditResponse:
        data[key] = redditResponse[key]

with open('resources/options.json', "w") as jsonFile:
    json.dump(data, jsonFile)

print(redditResponse)
