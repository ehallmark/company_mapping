
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
                    onShowResourceFunction($(document.body));
                    createResourceDynatable(resource);
                }
            });
        });
    });

});

var createResourceDynatable = function(resource) {
    $.ajax({
        url: '/init/datatable/'+resource,
        dataType: 'json',
        type: 'POST',
        success: function(data) {
            var $prevTable = $('table.dynatable');
            if($prevTable.length>0) {
                $prevTable.closest('.row').remove();
            }
            var $results = $('#results');
            //$results.empty();
            $results.append(data.result);
            var $table = $('table.dynatable');
            $table.bind('dynatable:afterUpdate', function() {
                onShowResourceFunction($table);

            }).dynatable({
                dataset: {
                    ajax: true,
                    ajaxUrl: 'dataTable.json',
                    ajaxOnLoad: true,
                    records: []
                },
                features: {
                    pushState: false,
                    paginate: true
                }
            });
        }
    });
};


var onShowResourceFunction = function($topElem) {
    $topElem.find('.resource-data-field.editable').dblclick(function(e) {
        e.stopPropagation();
        var $this = $(this);
        var fieldType = $this.attr('data-field-type');
        var resourceId = $this.attr('data-resource');
        var val = $this.attr('data-val');
        var attr = $this.attr('data-attr');
        var attrName = $this.attr('data-attrname');
        var id = $this.attr('data-id');
        $(document.body).dblclick(function() {
            $this.html(attrName+": "+val);
            $(this).off('dblclick');
        });
        var input = null;
        var inputTag = null;
        if (fieldType==='textarea') {
            input = "<textarea class='form-control'/>";
            inputTag = "textarea";
        } else if (fieldType==='text') {
            input = "<input type='text' class='form-control' />";
            inputTag = "input";
        } else if (fieldType==='number') {
            input = "<input type='number' class='form-control' />";
            inputTag = "input";
        } else if (fieldType==='boolean') {
            input = "<input type='checkbox' value='t' />";
            if(val && val!='false' && val != '(empty)') {
                input = "<input type='checkbox' checked='true' value='true' />";
            }
            inputTag = "input";
        }
        $this.html('<label>'+attrName+":"+input+"</label><span onclick='$(document.body).dblclick();' style='cursor: pointer;'>X</span><br /><button type='submit' class='btn btn-outline-secondary btn-sm'>Update</button>");
        var $input = $this.find(inputTag);
        var $btn = $this.find('button');
        $input.val(val);
        $btn.click(function(attr, attrName, $this, $input) {
            return function(e) {
                var value = $input.val();
                if(fieldType==='boolean') {
                    value = $input.is(':checked');
                }
                var data = {};
                data[attr] = value;
                $.ajax({
                    url: '/resources/'+resourceId+'/'+id,
                    dataType: 'json',
                    type: 'POST',
                    data: data,
                    success: function(showData) {
                        $this.html(attrName+": "+value);
                        $(document.body).off('dblclick');
                        $this.attr('data-val', value);
                    },
                    error: function() {
                        alert("An error occurred.");
                    }
                });
            };
        }(attr, attrName, $this, $input));
    });

    $topElem.find('.back-button').click(function(e) {
        e.preventDefault();
        var target = $(this).attr('data-target');
        target = $(target);
        target.trigger('click');
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
        var prepend = $form.attr('data-prepend');
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
                        if(prepend==='prepend') {
                            $(listRef).prepend(showData.template);
                        } else {
                            $(listRef).html(showData.template);
                        }
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

    $topElem.find('form.update-association').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var resourceId = $form.attr("data-resource");
        var associationId = $form.attr("data-association");
        var listRef = $form.attr('data-list-ref');
        var id = $form.attr('data-id');
        var formData = $form.serialize();
        var newId = $form.find('select').val();
        var prepend = $form.attr('data-prepend');
        var oldRef = "#node-"+associationId+"-"+newId.toString();
        $.ajax({
            url: '/new_association/'+resourceId+'/'+associationId+'/'+id+'/'+newId,
            dataType: 'json',
            data: formData,
            type: 'POST',
            success: function(showData) {
                var $oldRef = $(oldRef);
                if($oldRef.length) {
                    $(oldRef).html($(showData.template).unwrap());
                    onShowResourceFunction($(oldRef));
                } else {
                    if(prepend==='prepend') {
                        $(listRef).prepend(showData.template);
                    } else {
                        $(listRef).html(showData.template);
                    }
                    onShowResourceFunction($(listRef));
                }
                $form.find('select').val(null);
                $('.resource-new-link').filter(':visible').each(function() {
                    $(this).next().hide();
                });
            }
        });
        return false;
    });

    $('span.delete-node').click(function() {
        var $this = $(this);
        var id = $this.attr('data-id');
        var resourceId = $this.attr('data-resource');
        var associationName = $this.attr('data-association');
        var associationId = $this.attr('data-association-id');
        var associationRef = $this.attr('data-association-name');
        var url = '/resources_delete';
        $.ajax({
            url: url,
            dataType: 'json',
            type: 'POST',
            data: {
                associationRef: associationRef,
                resource: resourceId,
                id: id,
                association: associationName,
                association_id: associationId
            },
            success: function(showData) {
                $this.closest('div').remove();
            },
            error: function() {
                alert("An error occurred.");
            }
        });
    });


    $topElem.find('.multiselect-ajax').select2({
        closeOnSelect: true,
        ajax: {
            url: function() { return $(this).attr("data-url"); },
            dataType: "json",
            delay: 100,
            data: function(params) {
                var query = {
                    search: params.term,
                    page: params.page || 1
                };
                return query;
            }
        }
    });

    $topElem.find('.resource-show-link').click(showResourceByClickFunction);
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
                var id = showData.id;
                showResourceFunction(resourceId, id);
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

var showResourceFunction = function(resourceId, id) {
    $.ajax({
        url: '/resources/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(showData) {
            var $results = $('#results');
            $results.empty();
            $results.html(showData.template);
            onShowResourceFunction(($(document.body)));
            $('#results .nav.nav-tabs .nav-link').filter(':first').trigger('click');
        }
    });
};


var showResourceByClickFunction = function(e) {
    e.preventDefault();
    var resourceId = $(this).attr('data-resource');
    var id = $(this).attr('data-id')
    showResourceFunction(resourceId, id);
};


var createResourceList = function(resourceId, resourceName, data) {
    /*var $ul = $('<div></div>');
    for(var i = 0; i < data.length; i++) {
        var obj = data[i];
        var $li = $('<div><a href="#" style="cursor: pointer;">'+obj.data.name+"</a><span style='cursor: pointer;'>X</span></div>");
        $li.attr('data-id', obj.id);
        $li.attr('data-resource', resourceId);
        var $a = $li.find('a');
        $a.attr('data-id', obj.id);
        $a.attr('data-resource', resourceId);
        $a.click(showResourceByClickFunction);
        $li.find('span').click(function() {
            var $this = $(this);
            var id = $this.closest('div').attr('data-id');
            var url = '/resources/'+resourceId+'/'+id;
            $.ajax({
                url: url,
                dataType: 'json',
                type: 'DELETE',
                success: function(showData) {
                    $this.closest('div').remove();
                    var dynatable = $('table.dynatable').data('dynatable');
                    if(dynatable) {
                        $.ajax({
                            url: '/clear_dynatable',
                            dataType: 'json',
                            type: 'POST',
                            success: function() {
                                createResourceDynatable(resourceId);
                            }
                        });
                    }
                },
                error: function() {
                    alert("An error occurred.");
                }
            });
        });
        $ul.append($li);
    }*/
    var $result = $('<div class="col-12"></div>');
    $result.append('<h3>'+resourceName+'</h3>');
    var $new = createNewResourceForm(resourceId, resourceName);
    $result.append($new);
    //$result.append($ul);
    var $outer = $('<div class="row"></div>');
    $outer.append($result);
    return $outer;
};