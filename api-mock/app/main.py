from fastapi import FastAPI

from models.email_validation import EmailValidationRequest, EmailValidationResponse

app = FastAPI(title="User Management mock API",
              summary="Mock API",
              description="Some description")


@app.post("/emails")
def validate_email_domain(request: EmailValidationRequest) -> EmailValidationResponse:
    return EmailValidationResponse(status="success", allowed=True, message="Email domain is allowed")
