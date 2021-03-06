'use strict';
var express = require('express');

module.exports = function(app) {
	app.use('/', express.static(__dirname + '../../client/build/'));
	app.use('/auth', require('./auth'));
	app.use('/api/bankid', require('./api/bankid'));
	app.use('/api/documents', require('./api/documents'));
	app.use('/api/journal', require('./api/journal'));
	app.use('/api/places', require('./api/places/index'));
	app.use('/api/process-definitions', require('./api/process-definitions/index'));
	app.use('/api/process-form', require('./api/process-form'));
	app.get('/api/service', require('./api/service/index'));
	app.use('/api/service/flow', require('./api/service/flow'));
	app.use('/api/messages', require('./api/messages/index'));
	app.use('/api/services', require('./api/services'));
	app.post('/api/uploadfile', require('./api/uploadfile/post'));

	app.use('/', function(req, res, next) {
		res.render(__dirname + '../../client/build/index.html');
		next();
	});
};