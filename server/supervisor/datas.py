from random import randint
from typing import List, Set

from pydantic import BaseModel


def generate_id() -> int:
    return randint(0, 256)


class Worker(BaseModel):
    id: int
    host: str
    capacity: int = 1
    filled: int = 0

    worker_video_port: int
    worker_audio_port: int
    api_port: int


class Room(BaseModel):
    id: int = generate_id()
    users: Set[int] = set()
    creator_id: int
    worker_id: int


class User(BaseModel):
    id: int = generate_id()
    name: str


class RoomCreateBody(BaseModel):
    user_id: int


class RoomJoinBody(BaseModel):
    user_id: int
    room_id: int


class WorkerCreateBody(BaseModel):
    id: int
    host: str
    capacity: int = 1

    worker_video_port: int = 5001
    worker_audio_port: int = 5002
    api_port: int = 5000
