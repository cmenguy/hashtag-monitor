# Architecture

master-slave => only 1 connection per account allowed on Twitter public streaming API
master establishes connection and for each tweet:
* gets list of hashtags
* for each hashtag:
** compute a hash of the hashtag
** 