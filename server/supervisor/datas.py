import string
from random import choice
from typing import List

from pydantic import BaseModel


def random_short_id():
    symbols = string.digits + string.ascii_lowercase
    return ''.join(choice(symbols) for _ in range(5))


class Worker(BaseModel):
    id: str
    url: str
    capacity: int = 1
    filled: int = 0


class Room(BaseModel):
    id: str = random_short_id()
    users: List[str] = []  # todo: make set()?
    creator: str
    worker_id: str


class RoomCreateBody(BaseModel):
    creator: str


class RoomJoinBody(BaseModel):
    user: str
    room_id: str


class WorkerCreateBody(BaseModel):
    id: str
    url: str
    capacity: int = 1
