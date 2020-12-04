from random import randint
from typing import List

from pydantic import BaseModel


def generate_id() -> int:
    return randint(0, 256)


class Worker(BaseModel):
    id: str
    host: str
    capacity: int = 1
    filled: int = 0

    worker_video_port: int
    worker_audio_port: int


class Room(BaseModel):
    id: str = generate_id()
    users: List[str] = []
    creator_id: str
    worker_id: str


class User(BaseModel):
    id: str = generate_id()
    name: str


class RoomCreateBody(BaseModel):
    user_id: str


class RoomJoinBody(BaseModel):
    user_id: str
    room_id: str


class WorkerCreateBody(BaseModel):
    id: str
    host: str
    capacity: int = 1

    worker_video_port: int = 5001
    worker_audio_port: int = 5002
