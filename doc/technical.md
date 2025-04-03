# Basic Principles

### Command Format
```
Action(param1, param2, ...)
```
- Actions are case-sensitive (e.g., Move, Broadcast, Help)
- Parameters are comma-separated
- Parameters can be strings, coordinates, or numbers
- String parameters should be in double quotes

### Coordinate Format
```
(x,y)
```
- x and y are integers
- Range: -1000 to +1000
- Origin (0,0) is at center
- No spaces between numbers and commas

## Command Types

### 1. Move Command
```
Move(userID, currentPosition, destination)
```
Example:
```
Move(User1, (0,0), (1,1))
```
- Validates current position matches server record
- Updates user position in 2D array
- Broadcasts movement to all clients

### 2. Broadcast Command
```
Broadcast(userID, target, "message")
```
Example:
```
Broadcast(User1, all, "Hello World!")
```
- Message must be in double quotes
- Target can be "all" or specific userID
- Stores message history in userData

### 3. Help Command
```
Help()
```
- Takes no parameters
- Returns list of valid commands
- No authentication required

## Communication Flow

1. **Client to Server**
   - Client formats command string
   - Sends via TCP socket
   - Waits for server response

2. **Server Processing**
   - Parses command string
   - Validates format and parameters
   - Updates internal state
   - Broadcasts updates if needed

3. **Server to Client**
   - Formats response string
   - Broadcasts to all connected clients
   - Updates saved state in save.txt

## Error Handling

### Format Errors
- Invalid command format
- Missing parameters
- Malformed coordinates
- Unquoted strings

### Logic Errors
- Out of bounds coordinates
- Invalid current position
- Unknown userID
- Unknown target

## Benefits of String Protocol

1. **Human Readable**
   - Easy to debug
   - Self-documenting
   - Simple to implement

2. **Language Agnostic**
   - Works with any TCP client
   - Easy to implement in different languages
   - No special binary protocols needed

3. **Flexible**
   - Easy to add new commands
   - Simple to modify parameters
   - Supports different data types

## Implementation Notes

- Use regular expressions for validation
- Store command history
- Implement command queuing if needed
- Consider adding checksums for security