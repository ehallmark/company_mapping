
$(document).ready(function() {
    $('#main-menu .options button').each(function() {
        var $btn = $(this);
        var resource = $btn.attr('data-resource');
        $btn.click(function(e) {
            e.preventDefault();
            $.ajax({
                url: '/resources/'+resource,
                dataType: 'json',
                type: 'GET',
                success: function(data) {
                    var $results = $('#results');
                    $results.empty();
                    $results.html(createResourceList(resource, $btn.text(),data));
                }
            });
        });
    });
});


var onShowResourceFunction = function($topElem) {
    $topElem.find('.resource-data-field.editable').dblclick(function(e) {
        e.stopPropagation();
        var $this = $(this);
        var resourceId = $this.attr('data-resource');
        var val = $this.attr('data-val');
        var attr = $this.attr('data-attr');
        var attrName = $this.attr('data-attrname');
        var id = $this.attr('data-id');
        $(document.body).dblclick(function() {
            $this.html(attrName+": "+val);
            $(this).off('dblclick');
        });
        $this.html('<label>'+attrName+":<textarea class='form-control'/> </label><span onclick='$(document.body).dblclick();' style='cursor: pointer;'>X</span><br /><button type='submit' class='btn btn-outline-secondary btn-sm'>Update</button>");
        var $input = $this.find('textarea');
        var $btn = $this.find('button');
        $input.val(val);
        $btn.click(function(e) {
            var val = $input.val();
            var data = {};
            data[attr] = val;
            $.ajax({
                url: '/resources/'+resourceId+'/'+id,
                dataType: 'json',
                type: 'POST',
                data: data,
                success: function(showData) {
                    $this.html(attrName+": "+val);
                    $(document.body).off('dblclick');
                },
                error: function() {
                    alert("An error occurred.");
                }
            });
        });
    });

    $topElem.find('.resource-new-link').click(function(e) {
        e.preventDefault();
        $(this).next().slideToggle();
    });
    $topElem.find('form.association').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var resourceId = $form.attr("data-resource");
        var associationId = $form.attr("data-association");
        var id = $form.attr('data-id');
        var listRef = $form.attr('data-list-ref');
        var formData = $form.serialize();
        $.ajax({
            url: '/new/'+associationId,
            dataType: 'json',
            data: formData,
            type: 'POST',
            success: function(showData) {
                var newId = showData.id;
                $.ajax({
                    url: '/new_association/'+resourceId+'/'+associationId+'/'+id+'/'+newId,
                    dataType: 'json',
                    data: formData,
                    type: 'POST',
                    success: function(showData) {
                        $(listRef).prepend(showData.template);
                        $form.find('input.form-control').val(null);
                        onShowResourceFunction($(listRef));
                        $('.resource-new-link').filter(':visible').each(function() {
                            $(this).next().hide();
                        });
                    }
                });

            }
        });
        return false;
    });

    $topElem.find('.resource-show-link').click(showResourceByClickFunction);

    $('#results .nav.nav-tabs .nav-link').filter(':first').trigger('click');
};


var createNewResourceForm = function(resourceId, resourceName) {
    var $new = $('<a href="#">(New)</a>');
    var $form = $('<form style="display: none;" class="form-control"><label>Name:<br /><input class="form-control" type="text" name="name"/ ></label><button class="btn btn-outline-secondary btn-sm" type="submit">Create</button></form>')
    $form.submit(function(e) {
        e.preventDefault();
        $.ajax({
            url: '/new/'+resourceId,
            dataType: 'json',
            data: $form.serialize(),
            type: 'POST',
            success: function(showData) {
                $('#'+resourceName.toLowerCase()+"_index_btn").trigger('click');
            }
        });
        return false;
    });
    $new.click(function(e) {
        e.preventDefault();
        $form.slideToggle();
        return false;
    });
    return $('<div></div>').append($new).append($form);
};


var showResourceByClickFunction = function(e) {
    e.preventDefault();
    var resourceId = $(this).attr('data-resource');
    var id = $(this).attr('data-id')
    var $this = $(this);
    $.ajax({
        url: '/resources/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(showData) {
            var $results = $('#results');
            $results.empty();
            $results.html(showData.template);
            onShowResourceFunction(($(document.body)));
        }
    });
};


var createResourceList = function(resourceId, resourceName, data) {
    var $ul = $('<div></div>');
    for(var i = 0; i < data.length; i++) {
        var obj = data[i];
        var $li = $('<a href="#" style="cursor: pointer;">'+obj.data.name+"</a><br/>");
        $li.attr('data-id', obj.id);
        $li.attr('data-resource', resourceId);
        $li.click(showResourceByClickFunction);
        $ul.append($li);
    }
    var $result = $('<div></div>');
    $result.append('<h5>'+resourceName+'</h5>');
    var $new = createNewResourceForm(resourceId, resourceName);
    $result.append($new);
    $result.append($ul);
    return $result;
};