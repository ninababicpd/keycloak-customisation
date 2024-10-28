# Mono repo for FridayTalk: Tailoring Keycloak - How to customise authentication flow

## Running whole example in Docker

- Environment variables are used, and stored in .env file
- You should have locally that file (in keycloak package) with actual values
- Template for .env file can be found in [.env_template](./keycloak/.env_template)
- Do not commit local .env file to git

#### Navigate to package 'keycloak'

If you want to run api mock in container as well, un-comment api-mock service in [docker-compose.yml](./keycloak/docker-compose.yml). 
Otherwise, refer to [README.md](./api-mock/README.md)

`docker-compose up -d` or `docker-compose up -d --build` if you want to rebuild images

### Stopping and removing containers

`docker-compose down`

### Removing containers and volumes

`docker-compose down -v`
