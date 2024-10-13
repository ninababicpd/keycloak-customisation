ALLOWED_DOMAINS = {"productdock.com", "codecentric.com"}


def email_domain_allowed(email: str) -> bool:
    domain = email.split("@")[-1]
    return domain in ALLOWED_DOMAINS
