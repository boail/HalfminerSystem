# Halfminer REST Server
Bukkit plugin containing a REST HTTP server, responding in JSON.

## Current features
- Uses very light HTTP server ([nanohttpd](https://github.com/NanoHttpd/nanohttpd))
- Define port and whitelisted IP's via config
- Returns JSON messages
- **Commands**
  - GET /status/<playercount>
    - Get current player count
  - GET /uuid/<uuid|playername>
    - Get a players last known name from UUID or vice versa
    