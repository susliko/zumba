import uuid
from typing import List

from pydantic import BaseModel


class Worker(BaseModel):
    id: str
    url: str
    capacity: int = 1
    filled: int = 0


class Room(BaseModel):
    id: str = str(uuid.uuid4())
    users: List[str] = []  # todo: make set()
    creator: str
    worker_id: str


class RoomCreateBody(BaseModel):
    creator: str


class RoomJoinBody(BaseModel):
    user: str
    room_id: str
