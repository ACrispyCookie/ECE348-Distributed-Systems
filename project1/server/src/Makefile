CC = gcc
CFLAGS = -Wall -g -pthread

ifeq ($(DEBUG), 1)
    CFLAGS += -DDEBUG
endif

TARGET = server_app
OBJS = utilities.o internal_server.o server_api.o server_app.o 
HEADERS = server_api.h internal_server.h internal_server.h utilities.h

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)
