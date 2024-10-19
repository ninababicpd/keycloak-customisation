from pydantic import BaseModel, ConfigDict, EmailStr, Field
from pydantic.alias_generators import to_camel


class EmailValidationBaseModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class EmailValidationRequest(EmailValidationBaseModel):
    email: EmailStr


class EmailValidationResponse(EmailValidationBaseModel):
    domain_allowed: bool
    message: str
