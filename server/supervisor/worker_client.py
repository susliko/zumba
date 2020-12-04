from typing import Union

from aiohttp import ClientSession, ContentTypeError
from pydantic.main import BaseModel


class LowLevelAsyncClientMixin:
    def __init__(self, host: str, port: int, **kwargs):
        self.host = host
        self.port = port
        self._session = None

        super(LowLevelAsyncClientMixin, self).__init__(**kwargs)

    async def get_session(self):
        if self._session is None:
            self._session = ClientSession()
        return self._session

    async def _do_request(self, endpoint: str, method: str,
                          data: Union[BaseModel, str, None] = None, timeout: int = 20):
        data = data.dict() if isinstance(data, BaseModel) else data
        url = f'http://{self.host}:{self.port}{endpoint}'
        session = await self.get_session()

        async with session.request(method, url, data=data, timeout=timeout) as resp:
            try:
                response_dict = await resp.json()
            except ContentTypeError:
                response_dict = resp.content
            if resp.status >= 400:
                raise Exception("Code {:d} returned for {} {} endpoint".format(resp.status, method, endpoint))

            if response_dict:
                return response_dict


class WorkerClientAsync(LowLevelAsyncClientMixin):

    async def ping(self):
        await self._do_request('/ping', 'GET')
