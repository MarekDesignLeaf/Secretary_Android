"""Email sending stub for Secretary clean backend.

Required environment variables (none configured → dev mode only):
  SMTP_HOST       SMTP server hostname
  SMTP_PORT       SMTP port (default 587)
  SMTP_USER       SMTP username
  SMTP_PASSWORD   SMTP password
  SMTP_FROM       From address, e.g. noreply@yourdomain.com
  APP_BASE_URL    Base URL used in reset links, e.g. https://app.example.com

In development (SMTP_HOST not set) the raw token is returned by the API
so it can be used without an email service.  In production the raw token
is never returned; the email must be sent successfully.
"""

from __future__ import annotations

import os
import smtplib
import logging
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

logger = logging.getLogger(__name__)

_DEV_MODE = not bool(os.getenv("SMTP_HOST"))


def is_dev_mode() -> bool:
    """True when no SMTP is configured — raw token may be exposed via API."""
    return _DEV_MODE


def send_password_reset_email(*, to_email: str, reset_token: str) -> None:
    """Send a password-reset email.

    In dev mode this is a no-op (caller exposes token in API response).
    In production this must succeed; failures raise RuntimeError.
    """
    if _DEV_MODE:
        logger.info("[DEV] Password reset token for %s: %s", to_email, reset_token)
        return

    base_url = os.getenv("APP_BASE_URL", "").rstrip("/")
    reset_url = f"{base_url}/reset-password?token={reset_token}"

    host = os.environ["SMTP_HOST"]
    port = int(os.getenv("SMTP_PORT", "587"))
    user = os.environ["SMTP_USER"]
    password = os.environ["SMTP_PASSWORD"]
    from_addr = os.getenv("SMTP_FROM", user)

    msg = MIMEMultipart("alternative")
    msg["Subject"] = "Secretary — password reset"
    msg["From"] = from_addr
    msg["To"] = to_email

    text_body = (
        f"You requested a password reset.\n\n"
        f"Use this link (valid 30 minutes):\n{reset_url}\n\n"
        f"If you did not request this, ignore this message."
    )
    html_body = (
        f"<p>You requested a password reset.</p>"
        f'<p><a href="{reset_url}">Reset your password</a> (valid 30 minutes)</p>'
        f"<p>If you did not request this, ignore this message.</p>"
    )
    msg.attach(MIMEText(text_body, "plain"))
    msg.attach(MIMEText(html_body, "html"))

    try:
        with smtplib.SMTP(host, port) as smtp:
            smtp.starttls()
            smtp.login(user, password)
            smtp.sendmail(from_addr, [to_email], msg.as_string())
    except Exception as exc:
        raise RuntimeError(f"Failed to send reset email: {exc}") from exc
