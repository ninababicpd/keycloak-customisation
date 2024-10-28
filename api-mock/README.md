# Mock API to demonstrate call from customised Keycloak registration flow

## Running API mock natively
#### Navigate to your venv and to api-mock package

You can install requirements either with package manager (pip-tools) or without:

- Installing requirements with pip-tools:

    - Install pip-tools if you don't have it:
        - `pip install pip-tools`
    - Since requirements.txt is already generated, you can simply run:
        - `pip-sync`

- Installing requirements without pip-tools:
    - `pip install requirements.txt`

- Running Uvicorn Server:
    - With auto-reload (useful during development, shouldn't be used in prod):
        - `uvicorn app.main:app --reload --port 8023` you can also use different port (just make sure to set up env vars properly) 
    - Without auto-reload:
        - `uvicorn app.main:app --port 8023`
