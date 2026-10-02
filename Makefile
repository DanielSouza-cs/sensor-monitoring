.PHONY: build test up down logs simulate clean

build:
	./mvnw -B -q package -DskipTests

test:
	./mvnw -B verify

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f monitoring warehouse-1 warehouse-2

simulate:
	./scripts/simulate-sensors.sh

clean:
	./mvnw -B -q clean
