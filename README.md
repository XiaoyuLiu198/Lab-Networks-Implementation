# Implement Transmission Control Protocol (TCP)

Course assignment of implementing a Transmission Control Protocol based on UDP sockets. Please do not use this code for any purpose. The simplified version of protocol requires:

### Proper message format

<img width="570" alt="Screen Shot 2022-11-26 at 6 27 56 PM" src="https://user-images.githubusercontent.com/65391883/204113934-006bb31a-55d8-4dc3-b9e9-78b08f62b35d.png">

### Transmission details

Requirements of maximum number of retransmissions, Maximum Transmission Unit (MTU) of IP packet.
#### Timeout Computation
Sender will always update the timestamp field in packet with current time. Receiver acked will copy the timestamp . After sender received the ACK it will compute the round trip time and we are calculating the timeout based on it.
#### Update Connection State
3-way handshake at start and termination. Update ACK number in packet when sending payload.

<img width="218" alt="Screen Shot 2022-11-26 at 6 38 09 PM" src="https://user-images.githubusercontent.com/65391883/204114133-11471542-2bdf-4035-98db-1911d5e03b73.png">


### Testing
```
java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws>
```
remote receiver
```
java TCPend -p <port> -m <mtu> -c <sws> -f <file name>
```
