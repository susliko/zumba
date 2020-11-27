import string
from functools import wraps
from random import choice
from typing import Callable

from fastapi import HTTPException


def catch_exceptions(func: Callable):
    """
    Catches all exceptions raised in function

    Rise http exceptions like that:
    raise http_exceptions.NotFound('sadly')

    If excepted not http exception, return status is 500
    """

    @wraps(func)
    async def wrapper(*args, **kwargs):
        try:
            return await func(*args, **kwargs)
        except HTTPException as e:
            raise e
        except Exception as e:
            raise HTTPException(detail=repr(e), status_code=500)

    return wrapper


def random_short_id():
    symbols = string.digits + string.ascii_lowercase
    return ''.join(choice(symbols) for i in range(5))
