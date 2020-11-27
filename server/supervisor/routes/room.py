from typing import Dict

from fastapi import APIRouter, Depends, HTTPException

from supervisor.datas import Room, RoomCreateBody, RoomJoinBody
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions, choose_worker

room_router = APIRouter()


@room_router.post('/room/create/', response_model=Room)
@catch_exceptions
async def create(body: RoomCreateBody, db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    worker = choose_worker(id_to_worker)
    if worker is None:
        raise HTTPException(detail=f'No resources to create room, try later', status_code=503)

    new_room = Room(creator=body.creator, worker_id=worker.id)

    id_to_room = await db.get_rooms()
    id_to_room[new_room.id] = new_room

    worker.filled += 1
    id_to_worker[worker.id] = worker

    await db.set_rooms(id_to_room)
    await db.set_workers(id_to_worker)
    return new_room


@room_router.post('/room/join/', response_model=Room)
@catch_exceptions
async def join(body: RoomJoinBody, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    room = id_to_room.get(body.room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {body.room_id} not found', status_code=404)
    else:
        room.users.append(body.user)
        id_to_room[room.id] = room
        await db.set_rooms(id_to_room)
        return room


@room_router.post('/room/leave/', response_model=Room)
@catch_exceptions
async def leave(body: RoomJoinBody, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    room = id_to_room.get(body.room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {body.room_id} not found', status_code=404)
    else:
        room.users.remove(body.user)
        id_to_room[room.id] = room
        await db.set_rooms(id_to_room)
        return room


@room_router.get('/room/all/', response_model=Dict[str, Room])
@catch_exceptions
async def get_all_rooms(db: RedisClient = Depends(get_db)):
    rooms = await db.get_rooms()
    return rooms


@room_router.get('/room/id/{room_id}/', response_model=Room)
@catch_exceptions
async def get_room_by_id(room_id: str, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    room = id_to_room.get(room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {room_id} not found', status_code=404)
    else:
        return room
