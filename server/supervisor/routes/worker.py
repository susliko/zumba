from typing import Dict, List

from fastapi import APIRouter, Depends, HTTPException

from supervisor.datas import Room, Worker, WorkerCreateBody
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions

worker_router = APIRouter()


@worker_router.get('/worker/all/', response_model=Dict[int, Worker])
@catch_exceptions
async def get_all_workers(db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    return id_to_worker


@worker_router.get('/worker/id/{worker_id}/', response_model=Worker)
@catch_exceptions
async def get_worker_by_id(worker_id: int, db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    if worker_id in id_to_worker:
        return id_to_worker[worker_id]
    else:
        raise HTTPException(detail=f'Worker with id {worker_id} not found', status_code=404)


@worker_router.get('/worker/rooms/{worker_id}/', response_model=List[Room])
@catch_exceptions
async def get_rooms_for_worker(worker_id: int, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    rooms = [room for room in id_to_room.values() if room.worker_id == worker_id]
    return rooms


@worker_router.post('/worker/modify/', response_model=List[Room])
@catch_exceptions
async def modify_workers(body: WorkerCreateBody, db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    if body.id in id_to_worker:
        existing_worker = id_to_worker[body.id]
        existing_worker.id = body.id
        existing_worker.host = body.host
        existing_worker.capacity = body.capacity

        existing_worker.worker_video_port = body.worker_video_port
        existing_worker.worker_audio_port = body.worker_audio_port
        existing_worker.api_port = body.api_port

        id_to_worker[body.id] = existing_worker
    else:
        id_to_worker[body.id] = Worker(id=body.id,
                                       host=body.host,
                                       capacity=body.capacity,
                                       worker_video_port=body.worker_video_port,
                                       worker_audio_port=body.worker_audio_port,
                                       api_port=body.api_port,
                                       )
    await db.set_workers(id_to_worker)
