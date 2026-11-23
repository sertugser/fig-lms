# Moodle 5.3.1 on PHP 8.3 + Apache.
# Base image: Moodle HQ's official PHP/Apache image (all PHP extensions Moodle needs, amd64 + arm64).
FROM moodlehq/moodle-php-apache:8.3

ARG MOODLE_TAG=v5.3.1

COPY --from=composer:2 /usr/bin/composer /usr/local/bin/composer

# Moodle code lives in /var/www/moodle. Since Moodle 5.1 the web root is the public/ subfolder
# and Composer dependencies (vendor/) are installed next to it.
RUN git clone --depth 1 --branch "${MOODLE_TAG}" https://github.com/moodle/moodle.git /var/www/moodle \
 && cd /var/www/moodle \
 && COMPOSER_ALLOW_SUPERUSER=1 composer install --no-dev --classmap-authoritative --no-interaction --no-progress \
 && rm -rf .git /root/.composer \
 && mkdir -p /var/www/moodledata \
 && chown -R www-data:www-data /var/www/moodledata

ENV APACHE_DOCUMENT_ROOT=/var/www/moodle/public

COPY config.php /var/www/moodle/config.php
COPY fig-ws-setup.php /opt/fig/fig-ws-setup.php
COPY fig-entrypoint.sh /usr/local/bin/fig-entrypoint
RUN chmod +x /usr/local/bin/fig-entrypoint

WORKDIR /var/www/moodle

ENTRYPOINT ["fig-entrypoint"]
CMD ["apache2-foreground"]
