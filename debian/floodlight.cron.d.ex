#
# Regular cron jobs for the floodlight package
#
0 4	* * *	root	[ -x /usr/bin/floodlight_maintenance ] && /usr/bin/floodlight_maintenance
