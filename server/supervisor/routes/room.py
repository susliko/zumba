from typing import Dict

from fastapi import APIRouter, Depends, HTTPException

from supervisor.datas import Room, RoomCreateBody, RoomJoinBody
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions, choose_worker, get_user

# from supervisor.worker_client import WorkerClientAsync

room_router = APIRouter()


# worker_client = WorkerClientAsync('localhost', 5000)


@room_router.post('/room/create/')
@catch_exceptions
async def create(body: RoomCreateBody, db: RedisClient = Depends(get_db)):
    user = await get_user(body.user_id, db)
    id_to_worker = await db.get_workers()
    worker = choose_worker(id_to_worker)
    if worker is None:
        raise HTTPException(detail=f'No resources to create room, try later', status_code=503)

    new_room = Room(creator_id=body.user_id, worker_id=worker.id, users=[body.user_id])

    id_to_room = await db.get_rooms()
    id_to_room[new_room.id] = new_room

    worker.filled += 1
    id_to_worker[worker.id] = worker

    await db.set_rooms(id_to_room)
    await db.set_workers(id_to_worker)
    return {
        'room_id': new_room.id,
        'worker_host': worker.host,
        'receive_video_port': worker.receive_video_port,
        'receive_audio_port': worker.receive_audio_port,
        'send_video_port': worker.send_video_port,
        'send_audio_port': worker.send_audio_port,
    }


@room_router.post('/room/join/')
@catch_exceptions
async def join(body: RoomJoinBody, db: RedisClient = Depends(get_db)):
    user = await get_user(body.user_id, db)
    id_to_room = await db.get_rooms()
    room = id_to_room.get(body.room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {body.room_id} not found', status_code=404)
    else:
        room.users.append(body.user_id)
        id_to_room[room.id] = room

        id_to_worker = await db.get_workers()
        if room.worker_id in id_to_worker:
            worker = id_to_worker[room.worker_id]
        else:
            worker = choose_worker(id_to_worker)
            room.worker_id = worker.id

        await db.set_rooms(id_to_room)

        return {
            'worker_host': worker.host,
            'receive_video_port': worker.receive_video_port,
            'receive_audio_port': worker.receive_audio_port,
            'send_video_port': worker.send_video_port,
            'send_audio_port': worker.send_audio_port,
        }


@room_router.post('/room/leave/', response_model=Room)
@catch_exceptions
async def leave(body: RoomJoinBody, db: RedisClient = Depends(get_db)):
    user = await get_user(body.user_id, db)
    id_to_room = await db.get_rooms()
    room = id_to_room.get(body.room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {body.room_id} not found', status_code=404)
    else:
        room.users.remove(body.user_id)
        id_to_room[room.id] = room
        await db.set_rooms(id_to_room)
        return room


@room_router.get('/room/all/', response_model=Dict[int, Room])
@catch_exceptions
async def get_all_rooms(db: RedisClient = Depends(get_db)):
    rooms = await db.get_rooms()
    # await worker_client.ping()
    return rooms


@room_router.get('/room/id/{room_id}/')
@catch_exceptions
async def get_room_by_id(room_id: str, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    room = id_to_room.get(room_id)
    if room is None:
        raise HTTPException(detail=f'Room with id {room_id} not found', status_code=404)
    else:
        id_to_worker = await db.get_workers()
        if room.worker_id in id_to_worker:
            worker = id_to_worker[room.worker_id]
        else:
            worker = choose_worker(id_to_worker)
            room.worker_id = worker.id
            await db.set_rooms(id_to_room)

        users = await db.get_users()
        forgotten = []
        room_users = {}
        for id in room.users:
            if id in users:
                room_users[id] = users[id]
            else:
                forgotten.append(id)
        for f in forgotten:
            room.users.remove(f)
        await db.set_rooms(id_to_room)

        return {
            'worker_host': worker.host,
            'receive_video_port': worker.receive_video_port,
            'receive_audio_port': worker.receive_audio_port,
            'send_video_port': worker.send_video_port,
            'send_audio_port': worker.send_audio_port,
            'users': room_users,
        }
