from typing import Dict

from fastapi import APIRouter, Depends, HTTPException

from supervisor.datas import Room, RoomCreateBody, RoomJoinBody
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions

room_router = APIRouter()


@room_router.post('/room/create/')
@catch_exceptions
async def create(body: RoomCreateBody, db: RedisClient = Depends(get_db)) -> Room:
    workers = await db.get_workers()
    worker = workers[0]
    new_room = Room(creator=body.creator, worker_id=worker.id)
    id_to_room = await db.get_rooms()
    id_to_room[new_room.id] = new_room
    await db.set_rooms(id_to_room)
    worker.filled += 1  # todo: fix
    await db.set_workers(workers)
    return new_room


@room_router.post('/room/join/')
@catch_exceptions
async def join(body: RoomJoinBody, db: RedisClient = Depends(get_db)) -> Room:
    id_to_room = await db.get_rooms()
    room = id_to_room.get(body.room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {body.room_id} not found', status_code=404)
    else:
        room.users.append(body.user)
        id_to_room[room.id] = room
        await db.set_rooms(id_to_room)
        return room


@room_router.post('/room/get/all/')
@catch_exceptions
async def get_all_rooms(db: RedisClient = Depends(get_db)) -> Dict[str, Room]:
    rooms = await db.get_rooms()
    return rooms
