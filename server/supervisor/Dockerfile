FROM python:3.8-slim
RUN apt-get update && apt-get install -y gcc build-essential curl cron

# Set working directory
WORKDIR /usr/app

# Service specific requirements
COPY supervisor/requirements.txt ./requirements.txt
RUN pip install -r ./requirements.txt

## add common
#COPY common/ common/

# add app
COPY supervisor/ supervisor/

USER nobody
