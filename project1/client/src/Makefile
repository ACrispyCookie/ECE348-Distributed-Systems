CC = gcc
CFLAGS = -Wall -g

TARGET = client_app
OBJS = client_app.o client_api.o
HEADERS = client_api.h

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)