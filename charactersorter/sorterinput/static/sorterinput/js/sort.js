$(function(){
    function disableOnSubmit(selector) {
        $(selector).submit( function(event) {
            // disable to avoid double submission. Deferred, because a submit
            // button that is already disabled contributes no name/value pair.
            var buttons = $(this).find("button, input[type=submit]");
            window.setTimeout(function() {
                buttons.attr('disabled', true);
            }, 0);
        });
    }

    disableOnSubmit('#undo_form');
    disableOnSubmit('#sort_form');
});
