import json
from typing import Dict, List, Optional

from aioredis import Redis, create_redis

from supervisor.config import config
from supervisor.datas import Room, Worker


async def get_db():
    db = RedisClient()
    await db.setup()
    try:
        yield db
    finally:
        await db.finalize()


class RedisClient:
    def __init__(self):
        self.redis: Optional[Redis] = None

    async def setup(self) -> None:
        self.redis = await create_redis(config.REDIS_DSN, encoding='utf-8')
        # if len(await self.get_workers()) == 0:
        #     await self.set_workers({'0': Worker(id=0, url='')})

    async def finalize(self) -> None:
        self.redis.close()

    # Rooms

    async def get_rooms(self) -> Dict[str, Room]:
        id_to_room_raw = await self.redis.get('rooms')
        id_to_room = json.loads(id_to_room_raw if id_to_room_raw is not None else '{}')
        return {id: Room.parse_raw(room) for id, room in id_to_room.items()}

    async def set_rooms(self, id_to_room: Dict[str, Room]) -> None:
        await self.redis.set('rooms', json.dumps({id: room.json() for id, room in id_to_room.items()}))

    # Workers

    async def get_workers(self) -> Dict[str, Worker]:
        id_to_worker_raw = await self.redis.get('workers')
        id_to_worker = json.loads(id_to_worker_raw if id_to_worker_raw is not None else '{}')
        return {id: Worker.parse_raw(worker) for id, worker in id_to_worker.items()}

    async def set_workers(self, id_to_worker: Dict[str, Worker]) -> None:
        await self.redis.set('workers', json.dumps({id: worker.json() for id, worker in id_to_worker.items()}))
