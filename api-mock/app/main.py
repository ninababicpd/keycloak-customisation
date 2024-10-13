from fastapi import FastAPI

from services.email_validation_service import email_domain_allowed
from models.email_validation import EmailValidationRequest, EmailValidationResponse

app = FastAPI(title="User Management mock API",
              summary="Mock API",
              description="Some description")


@app.post("/emails")
def validate_email_domain(request: EmailValidationRequest) -> EmailValidationResponse:
    if email_domain_allowed(request.email):
        return EmailValidationResponse(domain_allowed=True, message="Email domain is allowed")
    else:
        return EmailValidationResponse(domain_allowed=False, message="Email domain is not allowed")
