from pydantic import BaseModel, ConfigDict, EmailStr
from pydantic.alias_generators import to_camel


class EmailValidationBaseModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel)


class EmailValidationRequest(EmailValidationBaseModel):
    email: EmailStr


class EmailValidationResponse(EmailValidationBaseModel):
    domain_allowed: bool
    message: str
