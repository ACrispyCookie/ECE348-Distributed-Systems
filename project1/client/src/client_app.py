import struct
import client_api

PRIMALITY_SERVICE = 0

def serializeRequest(numbers):
    count = len(numbers)
    fmt = f"!I{count}Q" 
    return struct.pack(fmt, count, *numbers)

def deserializeRequest(payload):
    # Header that contains the count
    count = struct.unpack("!I", payload[:4])[0]
    
    fmt = f"!{count}I"
    booleans = struct.unpack(fmt, payload[4:])
    
    return booleans

def main():
    print("Initializing API...")
    if (client_api.init("config.txt") == None):
        return

    try:
        while True:
            user_input = input("\nEnter numbers to test (comma-separated) or 'q' to quit: ").strip()
            
            if user_input.lower() == 'q':
                print("Exiting client...")
                break
            if not user_input:
                continue
                
            try:
                numbersToTest = [int(x.strip()) for x in user_input.split(',')]
            except ValueError:
                print("[APP] Invalid input! Please enter only integers separated by commas.")
                continue
            print(f"[APP] Asking server to check: {numbersToTest}")
            payload = serializeRequest(numbersToTest)    
            
            responsePayload = client_api.doRequestReply(PRIMALITY_SERVICE, payload)
            if (responsePayload == None):
                print("[APP] Failed to get response!")
                continue
            results = deserializeRequest(responsePayload)
            
            print("--- Results ---")
            for num, isPrime in zip(numbersToTest, results):
                status = "Prime" if isPrime else "Not Prime"
                print(f"{num:4} : {status}")        
    finally:
        client_api.cleanUp()

if __name__ == "__main__":
    main()