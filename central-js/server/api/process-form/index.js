var express = require('express');
var router = express.Router();

router.use(function(req, res, next) {
	process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
	var processForm = require('./index.controller');
	
	var config = require('../../config.js');
	var activiti = config.activiti;
	
	var options = {
		protocol: activiti.protocol,
		hostname: activiti.hostname,
		port: activiti.port,
		path: activiti.path,
		username: activiti.username,
		password: activiti.password,
		params: {
			url: req.query.url || null
		}
	};
	
	var callback = function(error, response, body) {
		res.send(body);
		res.end();
	}
	
	processForm.index(options, callback);
});

module.exports = router;