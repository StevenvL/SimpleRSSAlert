# SimpleRSSAlert
Checks RSS feed every specified interval and sends updates to Slack/Discord using webhook.

- Uses Java and Maven for accessing Reddit API as well as publishing to discord using webhooks.
- Python is used during the authentication process to get access_token, this might be ported to Java later on.

![Preview](https://github.com/StevenvL/SimpleRSSAlert/blob/master/src/main/resources/images/Discord_2021-01-30_22-13-41.jpg?raw=true)

**This is a personal project!! Do not expect any support; sorry!!**

# Setup
## Step 1)
- First you need to gain Reddit OAUTH Token, follow this guide for a detailed run down
- https://alpscode.com/blog/how-to-use-reddit-api/
- Read up to 'Getting an Access Token'

## Step 2)
- Fill out ``` redditUsernamePassword.yaml ``` with your infomration
- Make sure to turn off 2 Factor Authenticaion
- Then, run ```RedditOAuthRequest.py```
- This should print information similar to this 
    ``` {'access_token': '216912536673-vRVst4XgHf8SaYQrGlfWEd8zAOo', 'token_type': 'bearer', 'expires_in': 3600, 'scope': '*'}```

## Step 3)
- Use the infomration gathered from the last step to fill out the ```access_token``` field located in ```/src/main/resources/options.json```
- Use this time to create a discord/slack server
- Generate a Webhook by going into the server setttings
- Post this Webhook URL into the respective ```<Discord/Slack>Webhook``` field
- Populate the ```rssfeeds``` with Reddit URLs or RSS URLs
- Update the ```interval``` field (*time in hrs*)

## Step 4)
-Run ```SimpleRSSAlert.java```
- If this is the first time running it, it will post the 5 latest submissions from the feeds you have listed
- It will check at every ```interval``` specified and post new information to the respective webhook


# Libraries Used
- **JRAW**: The Java Reddit API Wrapper (https://github.com/mattbdean/JRAW) by *mattbdean*
- **TemmieWebhook**: Easy bindings for Discord Webhook API (https://github.com/MrPowerGamerBR/TemmieWebhook) by *MrPowerGamerBR*
 -- This repo has been archived by the author, the updated version can be found here https://github.com/MinnDevelopment/discord-webhooks/

# Current Status
- Currently only works with Reddit

# To-Do
- Add Slack Support
- Add support for general RSS feeds
- Format code and add comments
