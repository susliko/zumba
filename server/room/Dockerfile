FROM golang

ENV GO111MODULE=on

WORKDIR /app

COPY go.mod .
COPY go.sum .

RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build

EXPOSE 80
EXPOSE 5001
EXPOSE 5002
EXPOSE 5003

ENTRYPOINT ["/app/room"]
