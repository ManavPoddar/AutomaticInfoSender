var mysql = require('mysql')
// Let’s make node/socketio listen on port 3000
var io = require('socket.io').listen(3000)
// Define our db creds
var db = mysql.createConnection({
    host: 'sql12.freemysqlhosting.net',
    user: 'sql12232016',
    password: 'RYPIjLf67H',
    database: 'sql12232016'
})
 
// Log any errors connected to the db
db.connect(function(err){
    if (err) console.log(err)
})

var clientCount = 0
 
io.sockets.on('connection', function(socket){
	console.log("Client connected...");
    // Socket has connected, increase socket count
    clientCount++
 
    socket.on('disconnect', function() {
		console.log("Player disconnected...");
        // Decrease the socket count on a disconnect, emit
        clientCount--
    })
 
    socket.on('senddata', function(data){
        db.query('INSERT INTO UserInformation (IMSI, IMEI, NetworkOperator, CellId, Lattitude, Longitude, NetworkType,Available_Wifi_List,Saved_Wifi_List) VALUES (?, ?, ?, ?, ?, ?, ?, ?,?)', [data.IMSI, data.IMEI, data.network_operator, data.cell_id, data.lattitude, data.longitude, data.network_type, data.wifi_available, data.wifi_saved])
		//db.query('INSERT INTO UserInformation (WifiListAvailable) VALUES (?)', data.wifi_list_available)
		//db.query('INSERT INTO UserInformation (WifiListSaved) VALUES (?)', data.wifi_list_saved)
		console.log("data sent...")
    })
})