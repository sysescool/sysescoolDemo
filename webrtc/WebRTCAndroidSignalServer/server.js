'use strict'

var log4js = require('log4js');
var http = require('http');
var https = require('https');
var fs = require('fs');
var socketIo = require('socket.io');

var express = require('express');
var serveIndex = require('serve-index');

var USERCOUNT = 3;

log4js.configure({
    appenders: {
        file: {
            type: 'file',
            filename: 'app.log',
            layout: {
                type: 'pattern',
                pattern: '%r %p - %m',
            }
        }
    },
    categories: {
       default: {
          appenders: ['file'],
          level: 'debug'
       }
    }
});

var logger = log4js.getLogger();

var app = express();
app.use(serveIndex('./public'));
app.use(express.static('./public'));

//http server
var http_server = http.createServer(app);
http_server.listen(80, '0.0.0.0');

var options = {
        key : fs.readFileSync('./cert/rsa_private.key'),
        cert: fs.readFileSync('./cert/cert.pem')
}

//https server
var https_server = https.createServer(options, app);
var io = socketIo.listen(https_server);

// 信令服务器
io.sockets.on('connection', (socket)=> {
        //
        console.log("this is an connection");
        // 发送端到端消息 Offer, Answer, Candidate 消息
        socket.on('message', (room, data)=>{
                socket.to(room).emit('message',room, data);
        });
        // 加入房间
        socket.on('join', (room)=>{
                socket.join(room);
                var myRoom = io.sockets.adapter.rooms[room];
                var users = (myRoom)? Object.keys(myRoom.sockets).length : 0;
                console.log("[join] user number:" + users + "room:" + room);
                logger.debug('the user number of room is: ' + users);

                if (users < USERCOUNT) {
                        // 已加入房间
                        socket.emit('joined', room, socket.id); //发给除自己之外的房间内的所有人
                        if(users > 1){
                                // 其他用户加入房间
                                socket.to(room).emit('otherjoin', room, socket.id);
                                console.log("<otherjoin> room:" + room);
                        }
                } else {
                        socket.leave(room);
                        // 房间已满
                        socket.emit('full', room, socket.id);
                }
                //socket.emit('joined', room, socket.id); //发给自己
                //socket.broadcast.emit('joined', room, socket.id); //发给除自己之外的这个节点上的所有人
                //io.in(room).emit('joined', room, socket.id); //发给房间内的所有人
        });
        // 离开房间
        socket.on('leave', (room)=>{
                var myRoom = io.sockets.adapter.rooms[room];
                var users = (myRoom)? Object.keys(myRoom.sockets).length : 0;
                logger.debug('the user number of room is: ' + (users-1));
                //socket.emit('leaved', room, socket.id);
                //socket.broadcast.emit('leaved', room, socket.id);
                // 对方离开房间
                socket.to(room).emit('bye', room, socket.id);
                // 已离开房间
                socket.emit('leaved', room, socket.id);
                //io.in(room).emit('leaved', room, socket.id);
        });

});

https_server.listen(443, '0.0.0.0');