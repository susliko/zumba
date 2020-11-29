from functools import wraps
from typing import Callable, Dict, Optional

from fastapi import HTTPException

from supervisor.datas import Worker


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


def choose_worker(id_to_worker: Dict[str, Worker]) -> Optional[Worker]:
    if len(id_to_worker) == 0:
        return None
    fill_rate_with_worker = [(worker.filled / worker.capacity, worker) for worker in id_to_worker.values()]
    worker = min(fill_rate_with_worker, key=lambda x: x[0])[1]
    return worker
