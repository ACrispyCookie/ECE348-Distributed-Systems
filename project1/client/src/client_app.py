import struct
import client_api

PRIMALITY_SERVICE = 0

def serializeRequest(numbers):
    """Packs a list of Python integers into the AppRequestT format."""
    count = len(numbers)
    fmt = f"!I{count}I" 
    return struct.pack(fmt, count, *numbers)

def deserializeRequest(payload):
    """Unpacks the C AppResponseT payload into a list of Python booleans/ints."""
    # Header that contains the count
    count = struct.unpack("!I", payload[:4])[0]
    
    fmt = f"!{count}I"
    booleans = struct.unpack(fmt, payload[4:])
    
    return booleans

def main():
    client_api.init("config.txt")
    
    numbersToTest = [7, 10, 13, 100, 997]
    print(f"[APP] Asking server to check: {numbersToTest}")

    payload = serializeRequest(numbersToTest)    
    try:
        responsePayload = client_api.doRequestReply(PRIMALITY_SERVICE, payload)
        results = deserializeRequest(responsePayload)
        
        print("\n--- Results ---")
        for num, isPrime in zip(numbersToTest, results):
            status = "Prime" if isPrime else "Not Prime"
            print(f"{num:4} : {status}")
            
    except TimeoutError as e:
        print(f"[APP] Failed: {e}")
    finally:
        client_api.cleanUp()

if __name__ == "__main__":
    main()